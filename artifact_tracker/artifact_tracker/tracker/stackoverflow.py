# -*- coding: utf-8 -*-
from artifact_tracker import tracker_app, celery
from artifact_tracker.utils.message import post_to_ldn_inbox, template_as2
from artifact_tracker.tracker.tracker import Tracker
from datetime import datetime
import requests

PORTAL_NAME = "stackoverflow"
LOG = tracker_app.log


class StackOverflowTracker(Tracker):

    def get_events(self, **kwargs) -> bool:
        """
        Retrieves events from Stack Overflow.

        Gets owner and portal credentials from the db, retrieves the
        events from the API, parses them appropriately, serializes them
        into ActivityStream messages and posts them to the LDN Inbox.

        :param kwargs: only used for unit testing. When the `test_response`
        key is set, this method will not perform the API requests and
        use the mock response.
        :return: (bool): True if all the events were POSTed to the LDN
        inbox successfully, False otherwise.
        """

        LOG.debug(f"Executing {PORTAL_NAME} get events")
        if not self.users:
            LOG.debug("no users. exiting.")
            return False

        for user in self.users:
            actor_id = user.get("id")
            portal_user_id = user.get("userId")
            last_tracked = user.get("lastTracked")

            if not portal_user_id:
                LOG.debug("no portal user id. skipping.")
                continue

            headers = {
                "Accept-Encoding": "GZIP"
            }

            user_posts_url = self.portal.get("event_urls", {}).\
                get("user_posts_url").format(portal_user_id)

            resp = requests.get(user_posts_url, headers=headers)
            LOG.debug("getting user events: %s" % user_posts_url)

            if resp.status_code != 200:
                LOG.debug("non-200 response code received. "
                          "Updating tracker status and exiting.")
                self.update_tracker_status(
                    actor_id=actor_id,
                    status_code=resp.status_code,
                    completed=True)
                continue

            data = resp.json()
            acts = self.make_as2_payload(
                posts=data,
                actor_id=actor_id,
                prov_api_url=user_posts_url)

            self.update_tracker_status(
                actor_id=actor_id,
                status_code=resp.status_code,
                completed=True)
            post_to_ldn_inbox(
                events=acts,
                from_datetime=last_tracked,
                inbox_url=self.ldn_inbox_url)

    def make_as2_payload(self,
                         posts: iter,
                         actor_id: str,
                         prov_api_url: str):
        """
        Converts Stack Overflow response into ActivityStream message.

        :param annotations: list of annotations received from Stack Overflow
        API.
        :return: a generator list of ActivityStream messages.
        """

        for event in posts.get("items"):
            as2_payload = template_as2(self.event_base_url,
                                       self.portal_name)

            as2_payload["activity"]["prov:used"].\
                append({"@id": prov_api_url})
            actor = {}
            actor["id"] = actor_id
            actor["type"] = "Person"
            actor["url"] = event.get("owner", {}).get("link")
            image = {}
            image["type"] = "Link"
            image["href"] = event.get("owner", {}).get("profile_image")
            actor["image"] = image
            as2_payload["event"]["actor"] = actor

            target = {}
            target["id"] = self.portal.get("portal_url")
            target["type"] = ["Collection"]
            as2_payload["event"]["target"] = target

            published = datetime.fromtimestamp(event.get("creation_date")).\
                strftime("%Y-%m-%dT%H:%M:%SZ")

            as2_payload["event"]["published"] = published
            as2_payload["event"]["type"] = ["Add",
                                            "tracker:ArtifactInteraction",
                                            "tracker:Tracker"]

            obj = {}
            post_type = event.get("post_type")
            item_type = []
            if post_type == "question":
                item_type = ["Link", "Note", "schema:Question"]
            else:
                item_type = ["Link", "Note", "schema:Answer"]

            items = [{
                "type": item_type,
                "href": event.get("link")
            }]
            obj["totalItems"] = len(items)
            obj["items"] = items
            obj["type"] = "Collection"
            as2_payload["event"]["object"] = obj

            yield as2_payload


@celery.task
def run(**kwargs):
    tracker: Tracker = StackOverflowTracker(**kwargs)
    tracker.get_events()
