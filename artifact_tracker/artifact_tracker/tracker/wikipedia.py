# -*- coding: utf-8 -*-
from artifact_tracker import tracker_app, celery
from artifact_tracker.utils.message import post_to_ldn_inbox, template_as2
from artifact_tracker.tracker.tracker import Tracker
import requests

PORTAL_NAME = "wikipedia"
LOG = tracker_app.log


class WikipediaTracker(Tracker):

    def get_events(self, **kwargs) -> bool:

        LOG.debug(f"Executing {PORTAL_NAME} get events")

        if not self.users:
            LOG.debug("no users. exiting.")
            return False

        for user in self.users:
            actor_id = user.get("id")
            portal_username = user.get("username")
            last_tracked = user.get("lastTracked")

            if not portal_username:
                LOG.debug("username not configured. skipping.")
                continue

            user_contributions_url = self.portal.get("event_urls", {}).\
                get("contributions_url").format(portal_username)

            if last_tracked:
                # Dedup by apending query parameter to url
                LOG.debug("start date value found in tracker state db entry.")
                api_start = "&ucend={}"
                user_contributions_url += api_start.format(last_tracked)

            resp = requests.get(user_contributions_url)
            LOG.debug("getting user events: %s" % user_contributions_url)

            if resp.status_code != 200:
                LOG.debug("non-200 response code received. "
                          "Updating tracker status and exiting.")
                self.update_tracker_status(
                    actor_id=actor_id,
                    status_code=resp.status_code,
                    completed=True)
                return False

            data = resp.json()

            acts = self.make_as2_payload(
                events=data,
                actor_id=actor_id,
                portal_username=portal_username,
                prov_api_url=user_contributions_url)
            # last_tracked = data.get("query", {}).get("usercontribs")[0]\
            # .get("timestamp")
            if acts:
                self.update_tracker_status(
                    actor_id=actor_id,
                    status_code=resp.status_code,
                    completed=True)
                post_to_ldn_inbox(
                    events=acts,
                    from_datetime=last_tracked,
                    inbox_url=self.ldn_inbox_url)
            else:
                LOG.debug("{} portal has 0 events for {}.".format(
                    PORTAL_NAME, actor_id))
        return True

    def make_as2_payload(self,
                         events: iter,
                         actor_id: str,
                         portal_username: str,
                         prov_api_url: str):
        """
        Converts the Slideshare API response into ActivityStream message.

        :param events: the list of slides from the slideshare api response.
        :yield: a generator list of ActivityStream messages.
        """
        events_list = events.get("query", {}).get("usercontribs")
        if not bool(events_list):
            return []

        for event in events_list:
            as2_payload = template_as2(self.event_base_url,
                                       self.portal_name)

            as2_payload["activity"]["prov:used"]\
                .append({"@id": prov_api_url})

            actor = {}
            actor["url"] = "https://wikipedia.org/wiki/User:{}"\
                .format(portal_username)
            actor["type"] = "Person"
            actor["name"] = portal_username
            actor["id"] = actor_id
            as2_payload["event"]["actor"] = actor

            target = {}
            target["id"] = self.portal.get("portal_url")
            target["type"] = ["Collection"]
            as2_payload["event"]["target"] = target

            created_at = event.get("timestamp")
            as2_payload["event"]["published"] = created_at
            if event.get("new"):
                as2_payload["event"]["type"].extend(
                    ["Add",
                     "tracker:ArtifactCreation"])
            else:
                as2_payload["event"]["type"].extend(
                    ["Update",
                     "tracker:ArtifactInteraction"])
            as2_payload["event"]["type"].append("tracker:Tracker")
            obj = {}
            title = event.get("title").replace(" ", "_")
            items = []

            url_params = [{
                "params": "?oldid={}".format(event.get("revid")),
                "source": ""
            }, {
                "params": "?diff=prev&oldid={}".format(event.get("revid")),
                "source": "?diff=prev"
            }]
            for p in url_params:
                items.append(
                    {"type": ["Link", "Article", "schema:Article"],
                     "href":
                        "https://wikipedia.org/wiki/{}{}".format(title,
                                                                 p["params"]),
                     "OriginalResource":
                        "https://wikipedia.org/wiki/{}{}".format(title,
                                                                 p["source"]),
                     "Memento":
                        "https://wikipedia.org/wiki/{}{}".format(title,
                                                                 p["params"]),
                     "mementoDatetime": created_at
                     })

            obj["totalItems"] = len(items)
            obj["items"] = items
            obj["type"] = "Collection"

            as2_payload["event"]["object"] = obj

            yield as2_payload


@celery.task
def run(**kwargs):
    tracker: Tracker = WikipediaTracker(**kwargs)
    tracker.get_events()
