# -*- coding: utf-8 -*-
"""
The Github Event Tracker.
"""

from artifact_tracker import tracker_app, celery
from artifact_tracker.utils.message import post_to_ldn_inbox, template_as2
from artifact_tracker.tracker.tracker import Tracker
import requests

PORTAL_NAME = "github"
LOG = tracker_app.log

# a map of various object type names that github
# uses for each of the different event types that
# their API supports
EVENT_TYPES = {
    "CommitCommentEvent": "comment",
    "CreateEvent": "",
    "DeleteEvent": "delete",
    "DeploymentEvent": "deployment",
    "DeploymentStatusEvent": "deployment_status",
    "DownloadEvent": "download",
    "FollowEvent": "follow",
    "ForkEvent": "fork",
    "ForkApplyEvent": "fork_apply",
    "GistEvent": "gist",
    "GollumEvent": "gollum",
    "IssueCommentEvent": "comment",
    "IssuesEvent": "issue",
    "LabelEvent": "label",
    "MemberEvent": "member",
    "MembershipEvent": "membership",
    "MilestoneEvent": "milestone",
    "OrganizationEvent": "organization",
    "PageBuildEvent": "page_build",
    "PullRequestEvent": "pull_request",
    "PushEvent": "commits",
    "ReleaseEvent": "release",
    "RepositoryEvent": "repository",
    "StatusEvent": "status",
    "WatchEvent": "watch"
}

# a map of event type name and the corresponding
# method to invoke dynamically.
# TODO: ReleaseEvent, ForkEvent, DeleteEvent
EVENT_MAP = {
    "CreateEvent": "on_create_event",
    # "ReleaseEvent": "on_create_event",
    # "ForkEvent": "on_create_event",
    "WatchEvent": "on_watch_event",
    "PushEvent": "on_push_event",
    "IssuesEvent": "on_issues_event",
    "IssueCommentEvent": "on_issue_comment_event",
    "PullRequestEvent": "on_pull_request_event",
    "CommitCommentEvent": "on_commit_comment_event"
}


class GithubTracker(Tracker):
    """
    The Github event tracker.
    """
    def get_events(self, **kwargs) -> bool:
        """
        Retrieves events from Github.

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
            api_key = user.get("apiKey")
            api_secret = user.get("apiSecret")
            last_tracked = user.get("lastTracked")
            last_token = user.get("lastToken")

            headers = {}
            if last_token:
                # E-Tag header value is used by github
                # for event dedup at the API level
                # https://developer.github.com/v3/activity/events/
                LOG.debug("etag header value found.")
                headers["If-None-Match"] = last_token

            user_timeline_url = self.portal.get("event_urls", {}).\
                get("user_events_url").format(portal_username)

            user_timeline_url = user_timeline_url + \
                "?client_id={}&client_secret={}".format(api_key, api_secret)

            LOG.debug("getting user events: %s" % user_timeline_url)
            resp = kwargs.get("test_response")
            if not resp:
                try:
                    resp = requests.get(user_timeline_url,
                                        headers=headers)
                except Exception:
                    LOG.debug("Error retrieving response from API.")
                    continue

            if resp.status_code != 200:
                LOG.debug(
                    "non-200 response code received. updating tracker "
                    "status and exiting.")
                self.update_tracker_status(
                    actor_id=actor_id,
                    status_code=resp.status_code,
                    completed=True)
                continue

            received_events: list = resp.json()
            # LOG.debug(received_events)
            LOG.debug("received %s events." % len(received_events))
            # getting next pages of events from link header
            # only events from the past 90 days are returned by the API!
            next_page = resp.links.get("next")
            while next_page:
                LOG.debug("fetching next url found in lh: %s" % next_page)
                rec_events_resp = requests.get(next_page.get("url"),
                                               headers=headers)
                next_page = rec_events_resp.links.get("next")
                received_events.extend(rec_events_resp.json())
                LOG.debug("received %s events." % len(received_events))
            LOG.debug("Total events: %s" % len(received_events))

            etag = resp.headers.get("ETag", "").strip()
            acts = self.make_as2_payload(
                events=received_events,
                actor_id=actor_id,
                portal_username=portal_username,
                etag=etag
                )

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
                         events: iter,
                         actor_id: str,
                         portal_username: str,
                         etag: str):
        """
        Converts the GitHub API response into ActivityStream message.

        Invokes the appropriate method corresponding to the event type.
        Uses the EVENT_MAP dictionary.
        :param events: the list of events from the github api response.
        :return: a generator list of ActivityStream messages.
        """
        if not events:
            return
        for event in events:
            gh_event_type = event.get("type")

            LOG.debug("gh_event_type: %s" % gh_event_type)

            if not EVENT_MAP.get(gh_event_type):
                LOG.debug(f"Unsupported event for tracker: "
                          f"{gh_event_type}")
                continue
            yield getattr(self,
                          EVENT_MAP.get(gh_event_type))(event,
                                                        gh_event_type,
                                                        actor_id,
                                                        portal_username,
                                                        etag
                                                        )

    @staticmethod
    def _make_html_url(url):
        """
        Converts the API url in the GitHub response to HTML URL
        for use in the views.
        :param url: the API url.
        :return: the HTML url.
        """
        if not url:
            return
        url = url.replace("api.", "")
        url = url.replace("repos/", "")
        url = url.replace("users/", "")
        return url

    def get_actor_md(self,
                     md,
                     actor_id,
                     portal_username):
        """
        A utility method to construct the actor section of the
        AS2 message from the event metadata. The actor url is
        necessary for this, else empty actor section will be returned.

        :param md: the event metadata.
        :return: (dict) the actor section of the AS2 message.
        """
        actor = {}
        if not md.get("url") and not md.get("html_url"):
            return actor
        actor["url"] = md.get("html_url") or \
            GithubTracker._make_html_url(md.get("url"))
        actor["name"] = portal_username
        actor["type"] = "Person"
        actor["id"] = actor_id
        if md.get("avatar_url"):
            image = {}
            image["type"] = "Link"
            image["href"] = md.get("avatar_url")
            actor["image"] = image
        return actor

    def on_issues_event(self,
                        event,
                        event_type,
                        actor_id,
                        portal_username,
                        etag):
        """
        Converts the IssuesEvent Github API response to
        an AS2 message.
        :param event: (dict) The event from Github API
        :param event_type: (str) The type of event: IsssuesEvent
        :return: (dict) the converted AS2 message
        """
        as2_payload = {}
        if not event or not event_type:
            return as2_payload
        as2_payload = template_as2(
            self.event_base_url,
            PORTAL_NAME,
            last_token=etag)

        portal_url = self.portal.get("portal_url")
        as2_payload["activity"]["prov:used"].append({"@id": portal_url})

        payload = event.get("payload")
        event_type_prop = EVENT_TYPES.get(event_type)
        event_md = payload.get(event_type_prop)

        as2_payload["event"]["actor"] = self.get_actor_md(
            event.get("actor"),
            actor_id,
            portal_username)

        target = {}
        target["id"] = GithubTracker._make_html_url(
            event_md["repository_url"])
        target["type"] = ["Collection"]
        as2_payload["event"]["target"] = target

        as2_payload["event"]["published"] = event.get("created_at")
        as2_payload["event"]["type"] = ["Add",
                                        "tracker:ArtifactInteraction",
                                        "tracker:Tracker"]

        obj = {}
        items = [{
            "type": ["Link", "Article", "schema:Question"],
            "href": event_md.get("html_url")
        }]
        obj["totalItems"] = len(items)
        obj["items"] = items
        obj["type"] = "Collection"
        as2_payload["event"]["object"] = obj

        return as2_payload

    def on_watch_event(self,
                       event,
                       event_type,
                       actor_id,
                       portal_username,
                       etag):
        """
        Converts the WatchEvent GitHub API event to an AS2 message.
        Equivalent to starring a repository:
        https://developer.github.com/v3/activity/events/types/#watchevent

        :param event: (dict) The event from Github API
        :param event_type: (str) the type of event: WatchEvent
        :return: (dict) the converted AS2 message.
        """
        as2_payload = {}
        if not event or not event_type:
            return as2_payload

        as2_payload = template_as2(
            self.event_base_url,
            PORTAL_NAME,
            last_token=etag)

        payload = event.get("payload")
        event_type_prop = EVENT_TYPES.get(event_type)
        event_md = payload.get(event_type_prop)
        # if payload doesn't include author use different key
        if not event_md:
            event_md = event.get("actor")

        portal_url = self.portal.get("portal_url")
        as2_payload["activity"]["prov:used"].append({"@id": portal_url})

        target = {}
        target["id"] = GithubTracker._make_html_url(
            event.get("repo", {}).get("url"))
        target["type"] = ["Collection"]
        as2_payload["event"]["target"] = target

        as2_payload["event"]["actor"] = self.get_actor_md(
            event_md,
            actor_id,
            portal_username)
        as2_payload["event"]["published"] = event.get("created_at")
        as2_payload["event"]["type"] = ["Like",
                                        "tracker:ArtifactInteraction",
                                        "tracker:Tracker"]

        obj = {}
        items = [{
            "type": ["Link", "Document", "schema:SoftwareSourceCode"],
            "href": GithubTracker._make_html_url(
                event.get("repo", {}).get("url"))
        }]
        obj["totalItems"] = len(items)
        obj["items"] = items
        obj["type"] = "Collection"
        as2_payload["event"]["object"] = obj

        return as2_payload

    def on_push_event(self,
                      event,
                      event_type,
                      actor_id,
                      portal_username,
                      etag):
        """
        Converts the PushEvent GitHub API event to an AS2 message.

        :param event: (dict) The event from Github API
        :param event_type: (str) the type of event: PushEvent
        :return: (dict) the converted AS2 message.
        """
        as2_payload = {}
        if not event or not event_type:
            return as2_payload

        as2_payload = template_as2(
            self.event_base_url,
            PORTAL_NAME,
            last_token=etag)

        payload = event.get("payload")
        event_type_prop = EVENT_TYPES.get(event_type)
        event_md = payload.get(event_type_prop)

        portal_url = self.portal.get("portal_url")
        as2_payload["activity"]["prov:used"].append({"@id": portal_url})

        as2_payload["event"]["actor"] = self.get_actor_md(
            event.get("actor"),
            actor_id,
            portal_username)

        target = {}
        target["id"] = GithubTracker._make_html_url(
            event.get("repo").get("url"))
        target["type"] = ["Collection"]
        as2_payload["event"]["target"] = target

        as2_payload["event"]["published"] = event.get("created_at")
        as2_payload["event"]["type"] = ["Add",
                                        "tracker:ArtifactInteraction",
                                        "tracker:Tracker"]

        obj = {}
        if isinstance(event_md, list) and len(event_md) > 0:
            items = [{
                "type": ["Link", "Document", "schema:SoftwareSourceCode"],
                "href": GithubTracker._make_html_url(event_md[0].get("url"))
            }]
            obj["totalItems"] = len(items)
            obj["items"] = items
        obj["type"] = "Collection"
        as2_payload["event"]["object"] = obj

        return as2_payload

    def on_issue_comment_event(self,
                               event,
                               event_type,
                               actor_id,
                               portal_username,
                               etag):
        """
        Converts the IssueCommentEvent GitHub API event to an AS2 message.

        :param event: (dict) The event from Github API
        :param event_type: (str) the type of event: IssueCommentEvent
        :return: (dict) the converted AS2 message.
        """
        as2_payload = {}
        if not event or not event_type:
            return as2_payload

        as2_payload = template_as2(
            self.event_base_url,
            PORTAL_NAME,
            last_token=etag)

        payload = event.get("payload")
        event_type_prop = EVENT_TYPES.get(event_type)
        event_md = payload.get(event_type_prop)

        as2_payload["event"]["actor"] = self.get_actor_md(
            event_md.get("user"),
            actor_id,
            portal_username)

        portal_url = self.portal.get("portal_url")
        as2_payload["activity"]["prov:used"].append({"@id": portal_url})

        target = {}
        target["id"] = GithubTracker._make_html_url(
            payload.get("issue", {}).get("repository_url"))
        target["type"] = ["Collection"]
        as2_payload["event"]["target"] = target

        as2_payload["event"]["published"] = event.get("created_at")
        as2_payload["event"]["type"] = ["Add",
                                        "tracker:ArtifactInteraction",
                                        "tracker:Tracker"]

        obj = {}
        items = [{
            "type": ["Link", "Note", "schema:Comment"],
            "href": event_md.get("html_url")
        }]
        obj["totalItems"] = len(items)
        obj["items"] = items
        obj["type"] = "Collection"
        as2_payload["event"]["object"] = obj

        return as2_payload

    def on_pull_request_event(self,
                              event,
                              event_type,
                              actor_id,
                              portal_username,
                              etag):
        """
        Converts the PullRequestEvent GitHub API event to an AS2 message.

        Three possible AS2 activities by the actor. Accept, TentativeReject,
        or Offer.

        :param event: (dict) The event from Github API
        :param event_type: (str) the type of event: PullRequestEvent
        :return: (dict) the converted AS2 message.
        """
        as2_payload = {}
        if not event or not event_type:
            return as2_payload

        as2_payload = template_as2(
            self.event_base_url,
            PORTAL_NAME,
            last_token=etag)
        portal_url = self.portal.get("portal_url")
        as2_payload["activity"]["prov:used"].append({"@id": portal_url})

        payload = event.get("payload")
        event_type_prop = EVENT_TYPES.get(event_type)
        event_md = payload.get(event_type_prop)
        if event_md.get("user", {}).get("login") == portal_username:
            # User offered pull request
            as2_payload["event"]["actor"] = self.get_actor_md(
                event_md.get("user"),
                actor_id,
                portal_username)
            as2_payload["event"]["type"] = ["Offer",
                                            "tracker:ArtifactInteraction"]
        elif event_md.get("merged_by"):
            # Actor merged a pull request into the repository
            as2_payload["event"]["actor"] = self.get_actor_md(
                event_md.get("merged_by"),
                actor_id,
                portal_username)
            as2_payload["event"]["type"] = ["Accept",
                                            "tracker:ArtifactInteraction"]
        else:
            # Actor closed the issue, Tentatively Rejecting it
            # (could reopen later and be used)
            as2_payload["event"]["actor"] = self.get_actor_md(
                event.get("actor"),
                actor_id,
                portal_username)
            as2_payload["event"]["type"] = ["TentativeReject",
                                            "tracker:ArtifactInteraction"]
        as2_payload["event"]["type"].append("tracker:Tracker")

        target = {}
        target["id"] = GithubTracker._make_html_url(
            event_md.get("base", {})
            .get("repo", {}).get("html_url"))
        target["type"] = ["Collection"]
        as2_payload["event"]["target"] = target

        as2_payload["event"]["published"] = event.get("created_at")

        obj = {}
        items = [{
            "type": ["Link", "Document", "schema:SoftwareSourceCode"],
            "href": event_md.get("html_url")
        }]
        obj["totalItems"] = len(items)
        obj["items"] = items
        obj["type"] = "Collection"
        as2_payload["event"]["object"] = obj

        return as2_payload

    def on_commit_comment_event(self,
                                event,
                                event_type,
                                actor_id,
                                portal_username,
                                etag):
        """
        Converts the CommitCommentEvent GitHub API event to an AS2 message.

        :param event: (dict) The event from Github API
        :param event_type: (str) the type of event: CommitCommentEvent
        :return: (dict) the converted AS2 message.
        """
        as2_payload = {}
        if not event or not event_type:
            return as2_payload

        as2_payload = template_as2(
            self.event_base_url,
            PORTAL_NAME,
            last_token=etag)
        portal_url = self.portal.get("portal_url")
        as2_payload["activity"]["prov:used"].append({"@id": portal_url})

        payload = event.get("payload")
        event_type_prop = EVENT_TYPES.get(event_type)
        event_md = payload.get(event_type_prop)

        as2_payload["event"]["actor"] = self.get_actor_md(
            event_md.get("user"),
            actor_id,
            portal_username)

        target = {}
        target["id"] = GithubTracker._make_html_url(
            event.get("repo", {}).get("url"))
        target["type"] = ["Collection"]
        as2_payload["event"]["target"] = target

        as2_payload["event"]["published"] = event.get("created_at")
        as2_payload["event"]["type"] = ["Add",
                                        "tracker:ArtifactInteraction",
                                        "tracker:Tracker"]

        obj = {}
        items = [{
            "type": ["Link", "Note", "schema:Comment"],
            "href": event_md.get("html_url")
        }]
        obj["totalItems"] = len(items)
        obj["items"] = items
        obj["type"] = "Collection"
        as2_payload["event"]["object"] = obj

        return as2_payload

    def on_create_event(self,
                        event,
                        event_type,
                        actor_id,
                        portal_username,
                        etag):
        """
        Converts the CreateEvent GitHub API event to an AS2 message.

        :param event: (dict) The event from Github API
        :param event_type: (str) the type of event: CreateEvent
        :return: (dict) the converted AS2 message.
        """
        as2_payload = {}
        if not event or not event_type:
            return as2_payload

        as2_payload = template_as2(
            self.event_base_url,
            PORTAL_NAME,
            last_token=etag)
        portal_url = self.portal.get("portal_url")
        as2_payload["activity"]["prov:used"].append({"@id": portal_url})

        as2_payload["event"]["actor"] = self.get_actor_md(
            event.get("actor"),
            actor_id,
            portal_username)

        target = {}
        target["id"] = GithubTracker._make_html_url(
            event.get("actor", {}).get("url"))
        target["type"] = ["Collection"]
        as2_payload["event"]["target"] = target

        as2_payload["event"]["published"] = event.get("created_at")
        as2_payload["event"]["type"] = ["Create",
                                        "tracker:ArtifactCreation",
                                        "tracker:Tracker"]

        ref_type = event.get("payload", {}).get("ref_type", "")
        if ref_type == "tag":
            tag = ref_type = event.get("payload", {}).get("ref")
            href = "{}/releases/tag/{}".format(GithubTracker._make_html_url(
                event.get("repo", {}).get("url")), tag)
        else:
            href = GithubTracker._make_html_url(
                event.get("repo", {}).get("url"))

        obj = {}
        items = [{
            "type": ["Link", "Document", "schema:SoftwareSourceCode"],
            "href": href
        }]
        obj["totalItems"] = len(items)
        obj["items"] = items
        obj["type"] = "Collection"
        as2_payload["event"]["object"] = obj

        return as2_payload


@celery.task
def run(**kwargs):
    tracker: Tracker = GithubTracker(**kwargs)
    tracker.get_events()
