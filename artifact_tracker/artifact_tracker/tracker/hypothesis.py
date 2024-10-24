# -*- coding: utf-8 -*-
from artifact_tracker import tracker_app, celery
from artifact_tracker.utils.message import post_to_ldn_inbox, template_as2
from artifact_tracker.tracker.tracker import Tracker
from datetime import datetime
import requests


PORTAL_NAME = "hypothesis"
LOG = tracker_app.log


class HypothesisTracker(Tracker):

    def get_events(self, **kwargs) -> bool:
        """
        Retrieves events from Hypothesis.

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
            portal_username = user.get("username")
            last_tracked = user.get("lastTracked")

            if not portal_username:
                LOG.debug(f"{PORTAL_NAME} username not configured. exiting.")
                return False

            user_annotations_url = self.portal.get("event_urls", {}).\
                get("user_search_url").format(portal_username)

            resp = requests.get(user_annotations_url)
            LOG.debug("getting user events: %s" % user_annotations_url)

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
                annotations=data,
                actor_id=actor_id,
                portal_username=portal_username,
                prov_api_url=user_annotations_url)

            self.update_tracker_status(
                actor_id=actor_id,
                status_code=resp.status_code,
                completed=True)
            post_to_ldn_inbox(
                events=acts,
                from_datetime=last_tracked,
                inbox_url=self.ldn_inbox_url)
        return True

    def make_as2_payload(self,
                         annotations: iter,
                         actor_id: str,
                         portal_username: str,
                         prov_api_url: str
                         ):
        """
        Converts Hypothesis response into ActivityStream message.

        :param annotations: list of annotations received from Hypothesis API
        :return: a generator list of ActivityStream messages.
        """

        for event in annotations.get("rows", []):
            as2_payload = template_as2(self.event_base_url,
                                       self.portal_name)

            as2_payload["activity"]["prov:used"].\
                append({"@id": prov_api_url})
            actor = {}
            actor["name"] = portal_username
            actor["id"] = actor_id
            actor["type"] = "Person"
            actor["url"] = "{}users/{}".\
                format(self.portal.get("portal_url"),
                       portal_username)
            as2_payload["event"]["actor"] = actor

            target = {}
            target["id"] = self.portal.get("portal_url")
            target["type"] = ["Collection"]
            as2_payload["event"]["target"] = target

            # Convert ISO8601 timestamp string to datetime.
            published = datetime.strptime(event.get("created"),
                                          '%Y-%m-%dT%H:%M:%S.%f+00:00').\
                strftime("%Y-%m-%dT%H:%M:%SZ")

            as2_payload["event"]["published"] = published
            as2_payload["event"]["type"] = ["Add",
                                            "tracker:ArtifactInteraction",
                                            "tracker:Tracker"]

            obj = {}
            items = [{
                "type": ["Link", "Note", "schema:Comment"],
                "href": event.get("links", {}).get("html")
            }, {
                "type": ["Link", "Note", "schema:Comment"],
                "href": event.get("links", {}).get("incontext")
            }]
            obj["totalItems"] = len(items)
            obj["items"] = items
            obj["type"] = "Collection"
            as2_payload["event"]["object"] = obj

            yield as2_payload


@celery.task
def run(**kwargs):
    tracker: Tracker = HypothesisTracker(**kwargs)
    tracker.get_events()
