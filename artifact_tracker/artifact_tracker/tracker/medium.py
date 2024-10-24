# -*- coding: utf-8 -*-

from artifact_tracker import tracker_app, celery
from artifact_tracker.utils.message import post_to_ldn_inbox, template_as2
from artifact_tracker.tracker.tracker import Tracker
from datetime import datetime
import feedparser
import requests


PORTAL_NAME = "medium"
LOG = tracker_app.log


class MediumTracker(Tracker):

    def get_events(self, **kwargs):
        """
        Retrieves events from Medium.

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
                LOG.debug(f"{PORTAL_NAME} username not configured. skipping.")
                continue

            user_posts_url = self.portal.get("event_urls", {}).\
                get("posts_feed_url").format(portal_username)

            resp = requests.get(user_posts_url)

            LOG.debug("getting user events: %s" % user_posts_url)

            if resp.status_code != 200:
                LOG.debug("non-200 response code received. "
                          "Updating tracker status and continuing.")
                self.update_tracker_status(
                    actor_id=actor_id,
                    status_code=resp.status_code,
                    completed=True)
                continue

            feed = feedparser.parse(resp.content)

            last_updated = datetime.strptime(
                feed.get("feed", {}).get("updated"),
                "%a, %d %b %Y %H:%M:%S GMT")
            last_updated = last_updated.strftime("%Y-%m-%dT%H:%M:%SZ")

            if last_tracked:
                # Dedup by comparing feed last updated time to entry
                if last_tracked == last_updated:
                    LOG.debug("duplicate feed found. skipping as2 payload")
                    continue

            acts = self.make_as2_payload(
                feed=feed,
                actor_id=actor_id,
                portal_username=portal_username,
                prov_api_url=user_posts_url)

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
                         feed: dict,
                         actor_id: str,
                         portal_username: str,
                         prov_api_url: str):

        actor = {}
        actor_url = "https://medium.com/@{}".format(
            portal_username)
        actor["url"] = actor_url
        actor["type"] = "Person"
        actor["id"] = actor_id
        actor["name"] = portal_username

        image = {}
        image["type"] = "Link"
        image["href"] = feed.get("feed", {}).get("image", {}).get("href")
        actor["image"] = image
        target_name = feed.get("feed", {}).get("title")

        for event in feed.get("entries"):
            as2_payload = template_as2(self.event_base_url,
                                       self.portal_name)

            as2_payload["activity"]["prov:used"].\
                append({"@id": prov_api_url})
            as2_payload["activity"]["prov:used"][0]["prov:used"].append(
                {"id": "https://github.com/kurtmckee/feedparser"})
            as2_payload["event"]["actor"] = actor

            target = {}
            target["id"] = actor_url
            target["type"] = ["Collection"]
            target["name"] = target_name
            as2_payload["event"]["target"] = target

            pubtime = datetime.strptime(
                event.get("published"), "%a, %d %b %Y %H:%M:%S GMT")
            pubtime = pubtime.strftime("%Y-%m-%dT%H:%M:%SZ")
            as2_payload["event"]["published"] = pubtime
            as2_payload["event"]["type"] = ["Add",
                                            "tracker:ArtifactCreation",
                                            "tracker:Tracker"]

            obj = {}
            items = [{
                "type": ["Link", "Article", "schema:BlogPosting"],
                "href": event.get("link")
            }]
            obj["totalItems"] = len(items)
            obj["items"] = items
            obj["type"] = "Collection"
            as2_payload["event"]["object"] = obj

            yield as2_payload


@celery.task
def run(**kwargs):
    tracker: Tracker = MediumTracker(**kwargs)
    tracker.get_events()
