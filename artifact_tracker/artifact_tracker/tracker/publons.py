# -*- coding: utf-8 -*-
from artifact_tracker import tracker_app, celery
from artifact_tracker.utils.message import post_to_ldn_inbox, template_as2
from artifact_tracker.tracker.tracker import Tracker
from datetime import datetime
import requests
import logging

PORTAL_NAME = "publons"
LOG = tracker_app.log


class PublonsTracker(Tracker):

    def get_events(self, **kwargs) -> bool:
        """
        Retrieves events from Publons.

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
            portal_username = user.get("username")
            api_key = user.get("apiKey")
            last_tracked = user.get("lastTracked")

            if not portal_user_id:
                LOG.debug(f"{PORTAL_NAME} user id not configured. skipping.")
                continue

            user_posts_url = self.portal.get("event_urls", {}).\
                get("user_search_url").format(portal_user_id)

            headers = {
                "Authorization": f"Token {api_key}"
            }

            resp = requests.get(user_posts_url, headers=headers)

            LOG.debug("getting user events: {}".format(user_posts_url))

            if resp.status_code != 200:
                LOG.debug("non-200 response code received. "
                          "Updating tracker status and continuing.")
                self.update_tracker_status(
                    actor_id=actor_id,
                    status_code=resp.status_code,
                    completed=True)
                continue

            data = resp.json()
            acts = self.make_as2_payload(
                events=data,
                actor_id=actor_id,
                portal_user_id=portal_user_id,
                portal_username=portal_username,
                prov_api_url=user_posts_url)

            success = post_to_ldn_inbox(
                events=acts,
                from_datetime=last_tracked,
                inbox_url=self.ldn_inbox_url)

            while data["next"]:
                resp = requests.get(data["next"], headers=headers)
                data = resp.json()
                acts = self.make_as2_payload(
                    events=data,
                    actor_id=actor_id,
                    portal_user_id=portal_user_id,
                    portal_username=portal_username,
                    prov_api_url=user_posts_url)

                success = post_to_ldn_inbox(
                        events=acts,
                        from_datetime=last_tracked,
                        inbox_url=self.ldn_inbox_url)
                if not success:
                    break

            self.update_tracker_status(
                actor_id=actor_id,
                status_code=resp.status_code,
                completed=True)

        return True

    def make_as2_payload(self,
                         events: iter,
                         actor_id: str,
                         portal_user_id: str,
                         portal_username: str,
                         prov_api_url: str):
        """
        Converts Publons response into ActivityStream message.

        :param annotations: list of annotations received from Publons
        API.
        :return: a generator list of ActivityStream messages.
        """

        for event in events.get("results"):
            as2_payload = template_as2(self.event_base_url,
                                       self.portal_name)

            as2_payload["activity"]["prov:used"].\
                append({"@id": prov_api_url})
            actor = {}
            if portal_username:
                actor["name"] = portal_username
            actor["id"] = actor_id
            actor["type"] = "Person"
            actor["url"] = "https://publons.com/author/{}/".format(
                portal_user_id
            )
            as2_payload["event"]["actor"] = actor

            target = {}
            target["id"] = self.portal.get("portal_url")
            target["type"] = ["Collection"]
            as2_payload["event"]["target"] = target

            if event.get("date_reviewed") == str(datetime.now().year):
                as2_payload["event"]["published"] = as2_payload["event"][
                    "prov:generatedAtTime"]
            else:
                as2_payload["event"]["published"] = "{}-01-01T00:00:00Z"\
                    .format(event.get("date_reviewed"))
            as2_payload["event"]["type"] = ["Add",
                                            "tracker:ArtifactInteraction",
                                            "tracker:Tracker"]

            obj = {}
            items = [{
                "type": ["Link", "Note", "schema:Review"],
                "href": event.get("ids", {}).get("academic", {}).get("url")
            }]
            obj["totalItems"] = len(items)
            obj["items"] = items
            obj["type"] = "Collection"
            as2_payload["event"]["object"] = obj

            yield as2_payload


@celery.task
def run(**kwargs):
    tracker: Tracker = PublonsTracker(**kwargs)
    tracker.get_events()
