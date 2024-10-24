# -*- coding: utf-8 -*-

from artifact_tracker import tracker_app, celery
from artifact_tracker.utils.message import post_to_ldn_inbox, template_as2
from artifact_tracker.tracker.tracker import Tracker
import requests

PORTAL_NAME = "personal_website"
LOG = tracker_app.log


class PersonalWebsiteTracker(Tracker):

    def get_events(self, **kwargs):
        """
        Creates an events for a users Personal websites.

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
            portal_url = user.get("portalUrl")
            last_tracked = user.get("lastTracked")

            if not portal_url:
                LOG.debug(f"{PORTAL_NAME} url not configured. skipping.")
                continue

            LOG.debug("getting portal website user feed: %s" % portal_url)
            resp = requests.get(portal_url)

            if resp.status_code != 200:
                LOG.debug("non-200 response code received. "
                          "Updating tracker status and exiting.")
                self.update_tracker_status(
                    actor_id=actor_id,
                    status_code=resp.status_code,
                    completed=True)
                continue

            acts = self.make_as2_payload(
                actor_id,
                portal_url)

            self.update_tracker_status(
                actor_id=actor_id,
                status_code=resp.status_code,
                completed=True)

            post_to_ldn_inbox(acts,
                              from_datetime=last_tracked,
                              inbox_url=self.ldn_inbox_url)

        return True

    def make_as2_payload(self,
                         actor_id: str,
                         portal_url: str):

        as2_payload = template_as2(self.event_base_url,
                                   self.portal_name)

        # as2_payload["activity"]["prov:used"].\
        #     append({"@id": prov_api_url})
        actor = {}
        actor["url"] = portal_url
        actor["type"] = "Person"
        actor["id"] = actor_id
        as2_payload["event"]["actor"] = actor

        target = {}
        target["id"] = portal_url
        target["type"] = ["Collection"]
        as2_payload["event"]["target"] = target
        as2_payload["event"]["published"] = as2_payload["event"][
                "prov:generatedAtTime"]
        as2_payload["event"]["type"] = ["Update",
                                        "tracker:ArtifactInteraction",
                                        "tracker:Tracker"]

        obj = {}
        items = [{
            "type": ["Link", "WebPage", "schema:ProfilePage"],
            "href": portal_url
        }]
        obj["totalItems"] = len(items)
        obj["items"] = items
        obj["type"] = "Collection"
        as2_payload["event"]["object"] = obj

        yield as2_payload


@celery.task
def run(**kwargs):
    tracker: Tracker = PersonalWebsiteTracker(**kwargs)
    tracker.get_events()
