# -*- coding: utf-8 -*-

from elasticsearch import Elasticsearch
from researcher_pod.store.es.index import INDEX_NAME
from datetime import datetime


def index(es: Elasticsearch,
          user_id: str=None,
          msg_received_at: datetime=None,
          tracker_name: str=None,
          event_id: str=None,
          activity: dict=None
          ):
    """

    :param es:
    :param user_id:
    :param msg_received_at:
    :param tracker_name:
    :param payload_hash:
    :param activity:
    :return:
    """

    body = {}
    body["user_id"] = user_id
    if tracker_name:
        body["tracker_name"] = tracker_name
    published = datetime.strptime(
        activity["event"]["published"],
        "%Y-%m-%dT%H:%M:%SZ"
    ).strftime("%Y-%m-%dT%H:%M:%SZ")
    activity["event"]["published"] = published
    body["msg_published_at"] = published

    body["activity"] = activity
    body["msg_received_at"] = msg_received_at or \
        datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")

    return es.index(
        index=INDEX_NAME,
        id=event_id,
        doc_type="activities",
        refresh='wait_for',
        body=body
    )


def index_external(es: Elasticsearch,
                   event_id: str=None,
                   msg_received_at: datetime=None,
                   tracker_parent_id: str=None,
                   activity: dict=None):
    """
    Index archiver or capture as2 messages
    """
    body = {}
    body["tracker_parent_id"] = tracker_parent_id
    body["msg_published_at"] = activity["event"]["published"]
    body["msg_received_at"] = msg_received_at or \
        datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")

    body["activity"] = activity

    return es.index(
        index=INDEX_NAME,
        id=event_id,
        doc_type="activities",
        refresh='wait_for',
        body=body
    )


def exists(es: Elasticsearch,
           object_id: str=None,
           event_published: str=None):
    """

    :param es:
    :param payload_hash:
    :return:
    """
    body = {
        "query": {
            "bool": {
                "must": [{
                    "term": {
                        "activity.event.published": {
                            "value": event_published
                        }
                    }
                }, {
                    "nested": {
                        "path": "activity.event.object.items",
                        "query": {
                            "bool": {
                                "must": [{
                                    "match": {
                                        "activity.event.object.items.href":
                                            object_id
                                    }
                                }]
                            }
                        }
                    }
                }]
            }
        }
    }
    return es.search(index=INDEX_NAME,
                     body=body,
                     doc_type="activities")


def exists_in_year(es: Elasticsearch,
                   object_id: str=None,
                   year_published: str=None):
    """
    Dedup an event by object href and event publish
    year. Only used for tracker portals that only specify
    year of event rather than a complete datetime.
    """
    body = {
        "query": {
            "bool": {
                "must": [{
                    "range": {
                        "activity.event.published": {
                            "gte": year_published,
                            "lte": "now",
                            "format": "yyyy"
                        }
                    }
                }, {
                    "nested": {
                        "path": "activity.event.object.items",
                        "query": {
                            "bool": {
                                "must": [{
                                    "match": {
                                        "activity.event.object.items.href":
                                            object_id
                                    }
                                }]
                            }
                        }
                    }
                }]
            }
        }
    }

    return es.search(index=INDEX_NAME,
                     body=body,
                     doc_type="activities")


def event_exists(es: Elasticsearch,
                 tracker_id: str=None,
                 event_type: str=None):
    """
    Check index if capture/archiver event already
    exists for a tracker_id.
    """
    body = {
        "query": {
            "bool": {
                "must": [{
                    "term": {
                        "tracker_parent_id": {
                            "value": tracker_id
                        }
                    }
                }, {
                    "term": {
                        "activity.event.type": {
                            "value": event_type
                        }
                    }
                }
                ]
            }
        }
    }

    return es.search(index=INDEX_NAME,
                     body=body,
                     doc_type="activities")


def get(es: Elasticsearch,
        payload_hash: str=None):
    """

    :param es:
    :param payload_hash:
    :return:
    """
    return es.get(index=INDEX_NAME,
                  id=payload_hash,
                  doc_type="activities")


def search(es: Elasticsearch,
           event_type: str="tracker:Tracker",
           date: str=None,
           query: str=None,
           sort: str="msg_published_at:desc",
           size: int=200):
    """

    :param es:
    :param query:
    :param sort:
    :param size:
    :return:
    """

    body = {
        "query": {
            "bool": {
                "must": [{
                    "term": {
                        "activity.event.type": {
                            "value": event_type
                        }
                    }
                }
                ]
            }
        }
    }

    if date:
        body["query"]["bool"]["must"].append({
            "range": {
                "activity.event.published": {
                    "gte": date,
                    "lte": "now/d",
                    "format": "yyyyMMdd"
                }
            }
        })
        sort = "msg_published_at:asc"

    return es.search(
        index=INDEX_NAME,
        body=body,
        sort=sort,
        size=size
    )


def get_pipeline_events(es: Elasticsearch,
                        event_id: str=None):
    """
    Get tracker event by and other pipeline events
    that were informed by the tracker id.
    """
    body = {
        "query": {
            "bool": {
                "should": [
                    {"term": {"_id": event_id}},
                    {"term": {"tracker_parent_id": event_id}},
                    {"wildcard": {"activity.event.id": "*{}".format(event_id)}}
                ]
            }
        }
    }

    return es.search(
        index=INDEX_NAME,
        body=body,
        size=3,
        sort="msg_published_at:desc",
        doc_type="activities"
    )


def get_activity_by_id(es: Elasticsearch,
                       event_id: str=None,
                       event_type: str=None,
                       size: int=None,
                       date: str=None,
                       ):
    """
    Get event by event id and event type
    """
    body = {
        "query": {
            "bool": {
                "must": [{
                    "term": {
                        "activity.event.type": {
                            "value": event_type
                        }
                    }
                }
                ]
            }
        }
    }

    if event_id:
        body["query"]["bool"]["must"].append({
            "term": {
                "_id": {
                    "value": event_id
                }
            }
        })

    if date:
        body["query"]["bool"]["must"].append({
            "range": {
                "activity.event.published": {
                    "gte": date,
                    "lte": date,
                    "format": "yyyyMMdd"
                }
            }
        })

    return es.search(
        index=INDEX_NAME,
        body=body,
        size=size or 1,
        doc_type="activities"
    )


def aggregated_year_counts(es: Elasticsearch):
    body = {
        "aggs": {
            "activity.event.published": {
                "date_histogram": {
                    "field": "activity.event.published",
                    "interval": "1y",
                    "format": "yyyy"
                }
            }
        }
    }

    return es.search(
        index=INDEX_NAME,
        body=body,
        size=0,
        sort="msg_published_at:desc",
        doc_type="activities"
    )


def search_date_range(es: Elasticsearch,
                      selected_portals: [str]=None,
                      selected_date: str=None,
                      lower_bound: str="now-1y/d",
                      upper_bound: str="now/d",
                      sort: str=None,
                      size: int=None):
    """
    Return artifacts between a date range
    """
    if selected_date:
        lower_bound = selected_date
        upper_bound = selected_date

    body = {
        "query": {
            "bool": {
                "must": [{
                    "range": {
                        "activity.event.published": {
                            "gte": lower_bound,
                            "lte": upper_bound,
                            "format": "yyyy-MM-dd"
                        }
                    }
                }
                ]
            }
        }
    }
    if selected_portals:
        tracker_dict = {
            "bool": {
                "should": [
                ]
            }
        }
        for p in selected_portals:
            tracker_dict["bool"]["should"].append({
                "term": {"tracker_name": p}
            })
        body["query"]["bool"]["must"].append(tracker_dict)
    return es.search(
        index=INDEX_NAME,
        body=body,
        sort="msg_published_at:desc",
        size=size or 10000
    )


def user_search(es: Elasticsearch,
                size: int=200,
                user_id: str=None,
                sort: str="msg_published_at:desc",
                date: str=None
                ):
    body = {
        "query": {
            "bool": {
                "must": [{
                    "term": {
                        "user_id": {
                            "value": user_id
                        }
                    }
                }
                ]
            }
        }
    }

    if date:
        body["query"]["bool"]["must"].append({
            "range": {
                "activity.event.published": {
                    "gte": date,
                    "lte": "now/d",
                    "format": "yyyyMMdd"
                }
            }
        })
        sort = "msg_published_at:asc"

    return es.search(
        index=INDEX_NAME,
        body=body,
        sort=sort,
        size=size
    )


def delete_by_user(es: Elasticsearch,
                   user_id: str=None):
    """
    Delete all activities that have the user_id
    """
    body = {
        "query": {
            "bool": {
                "must": [{
                    "term": {
                        "user_id": {
                            "value": user_id
                        }
                    }
                }
                ]
            }
        }
    }

    return es.delete(
        index=INDEX_NAME,
        body=body
    )
