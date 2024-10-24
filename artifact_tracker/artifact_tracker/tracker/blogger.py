# -*- coding: utf-8 -*-

from artifact_tracker import tracker_app, celery
from artifact_tracker.utils.message import post_to_ldn_inbox, template_as2
from artifact_tracker.tracker.tracker import Tracker
from datetime import datetime
import requests

PORTAL_NAME = "blogger"
LOG = tracker_app.log


class BloggerTracker(Tracker):

    def get_events(self, **kwargs):
        """
        Retrieves events from Blogger.

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
            portal_url = user.get("portalUrl")
            api_key = user.get("apiKey")
            last_tracked = user.get("lastTracked")

            if not portal_url:
                LOG.debug(f"{PORTAL_NAME} url not configured. skipping.")
                continue

            if not portal_user_id:
                LOG.debug("user id not configured. skipping.")
                continue

            blog_domain_url = self.portal.get("event_urls", {}).\
                get("blog_domain_url").format(portal_url, api_key)
            resp = requests.get(blog_domain_url)
            LOG.debug("getting blogger user blogs: %s" % blog_domain_url)
            if resp.status_code != 200:
                LOG.debug("non-200 response code received. "
                          "Updating tracker status and exiting.")
                self.update_tracker_status(
                    actor_id=actor_id,
                    status_code=resp.status_code,
                    completed=True)
                continue

            data = resp.json()
            # skip portal if no posts
            if data.get("posts", {}).get("totalItems") == 0:
                LOG.debug("No blogs posts found. skipping.")
                self.update_tracker_status(
                    actor_id=actor_id,
                    status_code=None,
                    completed=True)
                continue

            last_updated = datetime.strptime(
                "".join(data.get("updated").rsplit(":", 1)),
                "%Y-%m-%dT%H:%M:%S%z")
            last_updated = last_updated.strftime("%Y-%m-%dT%H:%M:%SZ")

            if last_tracked:
                # Dedup by comparing blog last updated time to
                # last_tracked
                LOG.debug("last tracked date value found.")
                if last_tracked == last_updated:
                    LOG.debug(
                        "duplicate blog entries found. skipping as2 payload")
                    self.update_tracker_status(
                        actor_id=actor_id,
                        status_code=None,
                        completed=True)
                    continue

            blog_id = data.get("id")
            blog_posts_url = self.portal.get("event_urls", {}).\
                get("blog_posts_url").format(blog_id, api_key)

            posts_resp = requests.get(blog_posts_url)
            posts_data = posts_resp.json()
            if posts_resp.status_code != 200:
                LOG.debug("non-200 response code received. "
                          "Updating tracker status and exiting.")
                continue

            prov_url = self.portal.get("event_urls", {})\
                .get("blog_posts_url").format(blog_id, "")
            acts = self.make_as2_payload(
                    events=posts_data,
                    actor_id=actor_id,
                    portal_url=portal_url,
                    portal_user_id=portal_user_id,
                    prov_api_url=prov_url)
            while posts_data.get("nextPageToken"):
                nextPage = posts_data.get("nextPageToken")
                posts_resp = requests.get(
                    blog_posts_url + "&pageToken={}".format(nextPage))
                posts_data = posts_resp.json()
                if posts_resp.status_code != 200:
                    LOG.debug("non-200 response code received. "
                              "Updating tracker status and exiting.")
                    self.update_tracker_status(
                        actor_id=actor_id,
                        status_code=posts_resp.status_code,
                        completed=True)
                    break

                acts = self.make_as2_payload(
                    events=posts_data,
                    actor_id=actor_id,
                    portal_url=portal_url,
                    portal_user_id=portal_user_id,
                    prov_api_url=prov_url + "&pageToken={}".format(nextPage))

                post_to_ldn_inbox(acts,
                                  from_datetime=last_tracked,
                                  inbox_url=self.ldn_inbox_url)
            self.update_tracker_status(
                actor_id=actor_id,
                status_code=posts_resp.status_code,
                completed=True)
        return True

    def make_as2_payload(self,
                         events: dict,
                         actor_id: str,
                         portal_user_id: str,
                         portal_url: str,
                         prov_api_url: str):

        for event in events.get("items"):

            # skip entries that don't match the given user_id
            if event.get("author", {}).get("id") != portal_user_id:
                continue

            actor = {}
            actor["url"] = event.get("author", {}).get("url")
            actor["type"] = "Person"
            actor["id"] = actor_id
            actor["name"] = event.get("author", {}).get("displayName")

            as2_payload = template_as2(self.event_base_url,
                                       self.portal_name)

            as2_payload["activity"]["prov:used"].\
                append({"@id": prov_api_url})
            as2_payload["event"]["actor"] = actor

            target = {}
            target["id"] = portal_url
            target["type"] = ["Collection"]
            as2_payload["event"]["target"] = target

            pubtime = datetime.strptime(
                "".join(event.get("published").rsplit(":", 1)),
                "%Y-%m-%dT%H:%M:%S%z")
            pubtime = pubtime.strftime("%Y-%m-%dT%H:%M:%SZ")
            as2_payload["event"]["published"] = pubtime
            as2_payload["event"]["type"] = ["Add",
                                            "tracker:ArtifactCreation",
                                            "tracker:Tracker"]

            obj = {}
            items = [{
                "type": ["Link", "Article", "schema:BlogPosting"],
                "href": event.get("url")
            }]
            obj["totalItems"] = len(items)
            obj["items"] = items
            obj["type"] = "Collection"
            as2_payload["event"]["object"] = obj

            yield as2_payload


@celery.task
def run(**kwargs):
    tracker: Tracker = BloggerTracker(**kwargs)
    tracker.get_events()
