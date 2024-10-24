# -*- coding: utf-8 -*-
from artifact_tracker import tracker_app, celery
from artifact_tracker.utils.message import post_to_ldn_inbox, template_as2
from artifact_tracker.utils.secrets import get_ts_hash
from artifact_tracker.tracker.tracker import Tracker
import requests
from lxml import etree
from io import BytesIO
from datetime import datetime

PORTAL_NAME = "slideshare"
LOG = tracker_app.log


class SlideshareTracker(Tracker):

    def get_events(self, **kwargs) -> bool:

        LOG.debug(f"Executing {PORTAL_NAME} get events")
        if not self.users:
            LOG.debug("no users. exiting.")
            return False

        for user in self.users:
            actor_id = user.get("id")
            portal_username = user.get("username")
            api_key = user.get("apiKey")
            api_secret = user.get("apiSecret")
            last_tracked = user.get("lastTracked")
            last_token = user.get("lastToken")

            if not api_key or not api_secret:
                LOG.debug("API key or secret missing. skipping user.")
                continue

            url_param_tmpl = "%s=%s"
            url_params = []

            user_slides_url = self.portal.get("event_urls", {}).\
                get("user_slides_url")

            ts, api_hash = get_ts_hash(api_secret=api_secret)

            url_params.append(
                url_param_tmpl % ("api_key", api_key))
            url_params.append(
                url_param_tmpl % ("ts", ts))
            url_params.append(
                url_param_tmpl % ("hash", api_hash))
            url_params.append(
                url_param_tmpl % ("username_for", portal_username)
            )
            url_params.append(
                url_param_tmpl % ("detailed", 1)
            )

            # TODO: offset does not seem to work. find alternatives
            if last_token:
                # offset header value is used by slideshare for event dedup
                # at the API level this value is stored as the tracker state
                # in the db table:
                # https://www.slideshare.net/developers/documentation
                LOG.debug("offset value found.")
                url_params.append(
                    url_param_tmpl % (
                        "offset",
                        last_token
                    )
                )

            prov_api_url = user_slides_url + "?" + "&".join(url_params)
            resp = kwargs.get("test_response")
            if not resp:
                resp = requests.get(prov_api_url)
            m_data = BytesIO(resp.content)
            xml_data = etree.parse(m_data)
            events = xml_data.xpath("//User/Slideshow")
            events = self.make_as2_payload(
                events=events,
                actor_id=actor_id,
                portal_username=portal_username,
                prov_api_url=(
                    user_slides_url + "?" + "&".join(url_params[3:])),
                last_token=last_token
            )

            self.update_tracker_status(
                actor_id=actor_id,
                status_code=resp.status_code,
                completed=True)
            post_to_ldn_inbox(
                events,
                from_datetime=last_tracked,
                inbox_url=self.ldn_inbox_url)

        return True

    def make_as2_payload(self,
                         events: iter,
                         actor_id: str,
                         portal_username: str,
                         prov_api_url: str,
                         last_token: str
                         ):
        """
        Converts the Slideshare API response into ActivityStream message.

        :param events: the list of slides from the slideshare api response.
        :yield: a generator list of ActivityStream messages.
        """
        if not bool(events):
            return []

        for slide in events:
            as2_payload = template_as2(self.event_base_url,
                                       self.portal_name,
                                       last_token=last_token)

            as2_payload["activity"]["prov:used"].append({"@id": prov_api_url})

            actor = {}
            actor["url"] = f"https://slideshare.net/" \
                f"{portal_username}"
            actor["type"] = "Person"
            actor["name"] = portal_username
            actor["id"] = actor_id

            image = {}
            image["type"] = "Link"
            image["href"] = slide.find("ThumbnailSmallURL").text
            actor["image"] = image
            as2_payload["event"]["actor"] = actor

            target = {}
            target["id"] = actor.get("url")
            target["type"] = ["Collection"]
            as2_payload["event"]["target"] = target

            created_at = slide.find("Created").text
            c_date = datetime.strptime(created_at, "%Y-%m-%d %H:%M:%S %Z")
            as2_payload["event"]["published"] = c_date.strftime(
                "%Y-%m-%dT%H:%M:%SZ")
            as2_payload["event"]["type"] = ["Add",
                                            "tracker:ArtifactCreation",
                                            "tracker:Tracker"]

            obj = {}
            items = [{
                "type":
                    ["Link", "Article", "schema:PresentationDigitalDocument"],
                "href": slide.find("URL").text
            }]
            obj["totalItems"] = len(items)
            obj["items"] = items
            obj["type"] = "Collection"
            as2_payload["event"]["object"] = obj

            yield as2_payload


@celery.task
def run(**kwargs):
    tracker: Tracker = SlideshareTracker(**kwargs)
    tracker.get_events()
