# -*- coding: utf-8 -*-

from requests_oauthlib import OAuth1Session
from artifact_tracker import celery, tracker_app
# from artifact_tracker.user.utils import decrypt
from artifact_tracker.utils.message import post_to_ldn_inbox, template_as2
from artifact_tracker.tracker.tracker import Tracker
from datetime import datetime

PORTAL_NAME = "twitter"
LOG = tracker_app.log


class TwitterTracker(Tracker):

    def get_events(self, **kwargs):
        LOG.debug(f"executing {PORTAL_NAME} get event")

        if not self.users:
            LOG.debug("no users. exiting.")
            return False

        for user in self.users:
            actor_id = user.get("id")
            portal_username = user.get("username")
            api_key = user.get("apiKey")
            api_secret = user.get("apiSecret")
            oauth_token = user.get("oauthToken")
            oauth_secret = user.get("oauthSecret")
            last_tracked = user.get("lastTracked")
            last_token = user.get("lastToken")

            if not api_key or not api_secret:
                LOG.debug(
                    "{} API key or secret not configured. skipping."
                    .format(PORTAL_NAME))
                continue

            if not oauth_token or not oauth_secret:
                LOG.debug(
                    "{} OAUTH key or secret not configured. skipping."
                    .format(PORTAL_NAME))
                continue

            user_timeline_url = self.portal.get("event_urls", {})\
                .get("user_timeline_url").format(portal_username)
            since_tl_url = user_timeline_url
            first_track = True
            if last_token:
                # twitter uses "since_id" for dedup
                # so the largest tweet id that was received previously is
                # stored
                # https://dev.twitter.com/rest/reference/get/statuses/user_timeline
                LOG.debug("since_id found in tracker state.")
                since_tl_url += "&since_id=" + last_token
                first_track = False

            oauth = OAuth1Session(
                api_key,
                client_secret=api_secret,
                resource_owner_key=oauth_token,
                resource_owner_secret=oauth_secret)

            # fetching most recent 200 tweets. 200 is the max returned per req
            resp, timeline, since_id, max_id = self.get_twitter_response(
                oauth, since_tl_url)
            if not resp.status_code == 200 or len(timeline) == 0:
                self.update_tracker_status(
                    actor_id=actor_id,
                    status_code=resp.status_code,
                    completed=True)
                return
            activities = timeline[:]
            # going back in time to fetch older tweets, 200 at a time
            max_tl_url = user_timeline_url
            max_tl_url += "&max_id="
            if first_track:
                while resp.status_code == 200 and bool(max_id):
                    url = max_tl_url + max_id
                    LOG.debug("trying to get older tweets from url: %s" % url)
                    resp, timeline, since_id, max_id = \
                        self.get_twitter_response(oauth, url)
                    activities.extend(timeline)
                    if since_id == max_id:
                        break

            self.update_tracker_status(
                actor_id=actor_id,
                status_code=resp.status_code,
                completed=True)
            acts = self.make_as2_payload(
                events=activities,
                actor_id=actor_id,
                portal_username=portal_username,
                prov_api_url=user_timeline_url,
                last_token=last_token)
            post_to_ldn_inbox(
                events=acts,
                from_datetime=last_tracked,
                inbox_url=self.ldn_inbox_url
            )

    @staticmethod
    def get_twitter_response(oauth, url):
        twitter_response = oauth.get(url)
        timeline = twitter_response.json()

        LOG.debug("API response: %s" % twitter_response.status_code)
        if not twitter_response.status_code == 200 or len(timeline) == 0:
            LOG.debug(
                "received a non-200 or no timeline response from twitter.")
            return twitter_response, timeline, None, None

        LOG.debug("number of tweets: %s" % len(timeline))

        since_id = timeline[0].get("id_str")
        max_id = timeline[-1].get("id_str")
        LOG.debug("since_id: %s, max_id: %s" % (since_id, max_id))
        return twitter_response, timeline, since_id, max_id

    def make_as2_payload(self,
                         events: iter,
                         actor_id: str,
                         portal_username: str,
                         prov_api_url: str,
                         last_token: str):

        profile_url = "https://www.twitter.com/%s"
        tweet_url = "/status/%s"

        for event in events:
            as2_payload = template_as2(self.event_base_url,
                                       self.portal_name,
                                       last_token=last_token)
            as2_payload["activity"]["prov:used"].append({"@id": prov_api_url})

            actor = {}
            actor["url"] = profile_url % event.get(
                "user", {}).get("screen_name")
            actor["type"] = "Person"
            actor["id"] = actor_id
            actor["name"] = portal_username

            image = {}
            image["type"] = "Link"
            image["href"] = event.get("user", {}).get(
                "profile_image_url_https")
            actor["image"] = image
            as2_payload["event"]["actor"] = actor

            target = {}
            target["id"] = actor.get("url")
            target["type"] = ["Collection", "schema:Blog"]
            as2_payload["event"]["target"] = target

            created_at = event.get("created_at")
            c_date = datetime.strptime(created_at,
                                       "%a %b %d %H:%M:%S %z %Y")
            # Force naive datetime
            c_date = c_date.astimezone().replace(tzinfo=None)
            as2_payload["event"]["published"] = c_date.strftime(
                "%Y-%m-%dT%H:%M:%SZ")
            as2_payload["event"]["type"] = ["Create",
                                            "tracker:ArtifactCreation",
                                            "tracker:Tracker"]

            obj = {}
            url = actor.get("url") + tweet_url % event.get("id_str")
            items = [{
                "type": ["Link", "Note", "schema:SocialMediaPosting"],
                "href": url
            }]
            obj["totalItems"] = len(items)
            obj["items"] = items
            obj["type"] = "Collection"
            as2_payload["event"]["object"] = obj

            yield as2_payload


@celery.task
def run(**kwargs):
    tracker: Tracker = TwitterTracker(**kwargs)
    tracker.get_events()
