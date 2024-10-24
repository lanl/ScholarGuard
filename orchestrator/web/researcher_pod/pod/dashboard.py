# -*- coding: utf-8 -*-
from flask import Blueprint, render_template, send_file, jsonify, request
from researcher_pod.store.es.activity import search, search_date_range, aggregated_year_counts
from researcher_pod.store.artifact_stats import ArtifactStats
from researcher_pod.store.user import Portal
from researcher_pod.store.portal_config import PortalConfig
from operator import itemgetter
from researcher_pod import pod
from datetime import datetime
from lxml import etree
from io import BytesIO
import json
from werkzeug.utils import cached_property

LOG = pod.log
es = pod.es

dashboard_page = Blueprint("dashboard_page", __name__,
                           template_folder="templates")
NON_CROSSREF_TRACKER_NAMES = ["slideshare", "github",
                              "dblp"
                              ]
ARTIFACT_STATS_PORTALS = ["slideshare", "ms_academic"]
ES_STATS_PORTALS = ["github", "crossref", "dblp"]
ES_TOP_ARTIFACTS_PORTALS = ["github", "crossref"]

LABELS = {
    "ms_academic": "MS_Academic",
    "github": "GitHub",
    "crossref": "CrossRef",
    "dblp": "DBLP",
    "slideshare": "Slideshare"
}

COLORS = {
    "slideshare": "#3d3de0",
    "ms_academic": "#8de68d",
    "github": "#cf0101",
    "dblp": "#018001",
    "crossref": "#30ce30"
}


@dashboard_page.route("/dashboard/ss")
def on_ss():
    portal_id = get_portal_id(portal_name="slideshare")
    data, data_type = get_artifact_stats(portal_id=portal_id)
    dt = BytesIO(data)
    return send_file(dt, mimetype="application/xml")


class ES(object):
    @cached_property
    def es_activities(self):
        return search(pod.es)


@dashboard_page.route("/dashboard/top_artifacts/")
def on_top_artifacts():

    result = {}

    LOG.debug(ARTIFACT_STATS_PORTALS)
    for portal_name in ARTIFACT_STATS_PORTALS:
        label = LABELS[portal_name]
        result[label] = {}
        LOG.debug(f"getting stats for {portal_name}")
        portal_id = get_portal_id(portal_name=portal_name)
        if not portal_id:
            continue
        data, data_type = get_artifact_stats(portal_id=portal_id)
        stt = getattr(
            __import__("researcher_pod.pod.dashboard",
                       fromlist=["on_dashboard"]),
            "get_%s_top_artifacts" % portal_name.lower())(data, data_type)
        result[label] = stt

    es = ES()
    stt = get_es_top_5(es.es_activities)
    for s, st in stt.items():
        result[LABELS[s]] = get_top_5(st)
    return jsonify(result)


@dashboard_page.route("/dashboard/date_range/")
def artifacts_in_date_range():
    """
    Search a date range. Results map to a day.
    The has an artifact count and a list of actor_names.
    """
    # Results should be mapped to a day.
    # On the day have a count and list of actor_names
    selected_date: str = request.args.get("selected_date")
    selected_portals: str = request.args.get("selected_portals")

    if selected_date:
        print(selected_date)
    else:
        selected_date = ""
    if selected_portals:
        selected_portals = selected_portals.split(',')
    else:
        selected_portals = []

    # username = request.args.get('username')
    result = {}
    artifacts = search_date_range(
        es=es,
        selected_portals=selected_portals,
        selected_date=selected_date
    )
    # condense to date range
    for a in artifacts.get("hits").get("hits"):
        event = a.get("_source", {}).get("activity", {}).get("event")
        if event:
            publish_date = event.get("published")
            publish_date = datetime.strptime(
                publish_date, "%Y-%m-%dT%H:%M:%SZ")\
                .strftime("%Y-%m-%d")
            actor_name = event.get("actor", {}).get("name")
            result.setdefault(publish_date, {})
            result[publish_date].setdefault("actors", [])
            result[publish_date].setdefault("count", 0)
            result[publish_date]["count"] += 1
            if actor_name not in result[publish_date]["actors"]:
                result[publish_date]["actors"].append(actor_name)
    return jsonify(result)


@dashboard_page.route("/dashboard/agg_year_counts/")
def agg_year_counts():
    """
    Get aggregated activity counts for each year.
    To be used for filtering.
    """
    year_counts = aggregated_year_counts(es=es)
    year_counts = year_counts["aggregations"][
        "activity.event.published"]["buckets"]
    result = []
    for y in year_counts:
        result.append({
            "count": y["doc_count"],
            "year": y["key_as_string"]
        })
    result = sorted(result, key=itemgetter('year'), reverse=True)
    # print(json.dumps(result, indent=2))
    return jsonify(result)


@dashboard_page.route("/dashboard/impact/")
def on_impact_pie():
    result = {}
    result["impact"] = {}
    result["productivity"] = {}
    total_cc = []
    clrs = []
    for portal_name in ARTIFACT_STATS_PORTALS:
        LOG.debug(f"getting stats for {portal_name}")
        portal_id = get_portal_id(portal_name=portal_name)
        if not portal_id:
            continue
        data, data_type = get_artifact_stats(portal_id=portal_id)
        stt = getattr(
            __import__("researcher_pod.pod.dashboard",
                       fromlist=["on_dashboard"]),
            "get_%s_stats" % portal_name.lower())(data, data_type)
        LOG.debug(stt)
        total_cc.append(stt["total_cc"])
        clrs.append(COLORS.get(portal_name))

    es = ES()
    es_stats = get_es_portal_stats(es.es_activities)
    labels = ["SlideShare", "MS Academic"]
    labels.append("CrossRef")
    total_cc.append(es_stats["crossref"]["total_cc"])
    clrs.append(COLORS.get("crossref"))

    labels.append("GitHub")
    total_cc.append(es_stats["github_impact"]["total_cc"])
    clrs.append(COLORS.get("github"))

    result["impact"]["labels"] = labels
    result["impact"]["total_cc"] = total_cc
    result["impact"]["colors"] = clrs

    result["productivity"]["labels"] = ["GitHub", "DBLP", "SlideShare"]
    tc = []
    tc.append(es_stats["github_prod"]["total_cc"])
    tc.append(es_stats["dblp"]["total_cc"])
    tc.append(es_stats["slideshare"]["total_cc"])
    result["productivity"]["total_cc"] = tc
    result["productivity"]["colors"] = [
        COLORS.get("github"),
        COLORS.get("dblp"),
        COLORS.get("slideshare")
    ]
    return jsonify(result)


def get_portal_id(portal_name: str=None) -> int:

    if not isinstance(portal_name, str):
        return 0
    portal = Portal.query.filter_by(portal_name=portal_name).first()
    if not portal:
        return 0
    LOG.debug(portal.id)
    return portal.id


def get_es_portal_stats(activities):
    stats = {}
    activities = activities.get("hits").get("hits")
    stats["crossref"] = {}
    stats["crossref"]["total_cc"] = 0
    stats["github_impact"] = {}
    stats["github_impact"]["total_cc"] = 0
    stats["github_prod"] = {}
    stats["github_prod"]["total_cc"] = 0
    for name in NON_CROSSREF_TRACKER_NAMES:
        stats[name] = {}
        stats[name]["total_cc"] = 0
    for acts in activities:
        if acts["_source"]["tracker_name"] not in NON_CROSSREF_TRACKER_NAMES:
            stats["crossref"]["total_cc"] += 1
        elif acts["_source"]["tracker_name"] == "github":
            if acts["_source"]["activity"]["type"] in ["Create", "Update"]:
                stats["github_prod"]["total_cc"] += 1
            else:
                stats["github_impact"]["total_cc"] += 1
        else:
            stats[acts["_source"]["tracker_name"]]["total_cc"] += 1
    LOG.debug(stats)
    return stats


def get_artifact_stats(portal_id: int=None) -> (object, str):
    LOG.debug(f"getting artifact stats for {portal_id}")
    art_stat = ArtifactStats.query.filter_by(portal_id=portal_id).first()
    if art_stat:
        return art_stat.data, art_stat.data_type
    return None, None


def get_top_5(stats: dict) -> dict:
    sts = [(val.get("total_cc"), url) for url, val in stats.items()]
    sls = sorted(sts, key=lambda x: x[0], reverse=True)[:5]
    res = {}
    res["href"] = [stats[s[1]]["href"] for s in sls]
    res["total_cc"] = [stats[s[1]]["total_cc"] for s in sls]
    return res


def get_slideshare_top_artifacts(data, data_type) -> dict:
    LOG.debug(f"slideshare recd data of type {data_type}")
    stats = {}
    if not data_type == "xml":
        return stats

    m_data = BytesIO(data)
    xml_data = etree.parse(m_data)
    events = xml_data.xpath("//User/Slideshow")
    LOG.debug(f"slideshare events: {len(events)}")
    for slide in events:
        cc = 0
        url = slide.find("URL").text
        title = slide.find("Title").text
        stats[url] = {}
        stats[url]["href"] = f"<a href='{url}' target='_blank'>{title}</a>"
        if slide.find("NumDownloads") is not None:
            cc += int(slide.find("NumDownloads").text)
        if slide.find("NumViews") is not None:
            cc += int(slide.find("NumViews").text)
        if slide.find("NumComments") is not None:
            cc += int(slide.find("NumComments").text)
        if slide.find("NumFavorites") is not None:
            cc += int(slide.find("NumFavorites").text)
        stats[url]["total_cc"] = cc
    return get_top_5(stats)


def get_ms_academic_top_artifacts(data, data_type) -> dict:
    LOG.debug(f"ms_academic recd data of type {data_type}")
    stats = {}
    es = ES()
    if not data_type == "json":
        return stats
    data = json.loads(data)

    for pubs in data.get("entities"):
        ext = json.loads(pubs.get("E"))
        # LOG.debug(ext)
        doi = ext.get("DOI")
        if not doi:
            continue
        if not doi.startswith("https://doi.org/"):
            doi = "https://doi.org/" + doi
        title = pubs["Ti"]
        stats[doi] = {}
        stats[doi]["href"] = f"<a href='{doi}' target='_blank'>{title}</a>"
        stats[doi]["total_cc"] = pubs.get("CC")

    LOG.debug(stats)
    return get_top_5(stats)


def get_es_top_5(activities):
    stats = {}
    activities = activities.get("hits").get("hits")
    stats["crossref"] = {}
    stats["github"] = {}
    for name in ES_TOP_ARTIFACTS_PORTALS:
        stats[name] = {}
    for acts in activities:

        if acts["_source"]["tracker_name"] not in NON_CROSSREF_TRACKER_NAMES:
            url = acts["_source"]["activity"]["object"].get("url") or \
                acts["_source"]["activity"]["object"]["id"]
            if not stats["crossref"].get(url):
                stats["crossref"][url] = {}
                title = acts["_source"]["activity"]["object"].get("content") or \
                    url
                title = title[:50] + "..."
                stats["crossref"][url]["total_cc"] = 0
                stats["crossref"][url]["href"] = f"<a href='{url}' target='_blank'>{title}</a>"
            stats["crossref"][url]["total_cc"] += 1
        elif acts["_source"]["tracker_name"] == "github":
            if acts["_source"]["activity"]["type"] not in ["Create", "Update"]:
                if acts["_source"]["activity"].get("inReplyTo"):
                    url = acts["_source"]["activity"]["inReplyTo"]["id"]
                else:
                    url = acts["_source"]["activity"]["object"].get("url") or \
                        acts["_source"]["activity"]["object"]["id"]
                if not stats["github"].get(url):
                    stats["github"][url] = {}
                    title = url
                    title = title[:50] + "..."
                    stats["github"][url]["total_cc"] = 0
                    stats["github"][url]["href"] = f"<a href='{url}' target='_blank'>{title}</a>"
                stats["github"][url]["total_cc"] += 1
        elif acts["_source"]["tracker_name"] in ES_TOP_ARTIFACTS_PORTALS:
            tn = acts["_source"]["tracker_name"]
            url = acts["_source"]["activity"]["object"].get("url") or \
                acts["_source"]["activity"]["object"]["id"]
            if not stats[tn].get(url):
                stats[tn][url] = {}
                title = acts["_source"]["activity"]["object"]["content"]
                title = title[:50] + "..."
                stats[tn][url]["total_cc"] = 0
                stats[tn][url]["href"] = f"<a href='{url}' target='_blank'>{title}</a>"
            stats[tn][url]["total_cc"] += 1
    LOG.debug(stats)
    return stats


def get_slideshare_stats(data, data_type) -> dict:
    LOG.debug(f"slideshare recd data of type {data_type}")
    stats = {}
    if not data_type == "xml":
        return stats

    m_data = BytesIO(data)
    xml_data = etree.parse(m_data)
    events = xml_data.xpath("//User/Slideshow")
    stats["total_cc"] = 0
    cc = 0
    LOG.debug(f"slideshare events: {len(events)}")
    for slide in events:
        if slide.find("NumDownloads") is not None:
            cc += int(slide.find("NumDownloads").text)
        if slide.find("NumViews") is not None:
            cc += int(slide.find("NumViews").text)
        if slide.find("NumComments") is not None:
            cc += int(slide.find("NumComments").text)
        if slide.find("NumFavorites") is not None:
            cc += int(slide.find("NumFavorites").text)
    stats["total_cc"] = cc
    return stats


def get_ms_academic_stats(data, data_type) -> dict:
    LOG.debug(f"ms_academic recd data of type {data_type}")
    stats = {}
    if not data_type == "json":
        return stats
    data = json.loads(data)
    stats["total_cc"] = 0
    for pubs in data.get("entities"):
        stats["total_cc"] += pubs.get("CC")

    LOG.debug(stats)
    return stats
