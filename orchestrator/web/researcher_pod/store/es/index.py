# -*- coding: utf-8 -*-

from elasticsearch import Elasticsearch

INDEX_NAME = "pod"
MAPPING = {
    "settings": {
        "index": {
            "number_of_shards": 1,
            "max_result_window": 500000
        }
    },
    "mappings": {
        "activities": {
            "_all": {"enabled": False},
            "properties": {
                "user_id": {"type": "keyword"},
                "msg_published_at": {
                    "type": "date"
                },
                "msg_received_at": {
                    "type": "date"
                },
                "tracker_parent_id": {
                    "type": "keyword"
                },
                "tracker_name": {"type": "keyword"},
                "activity": {
                    "properties": {
                        "@context": {
                            "enabled": False
                        },
                        "event": {
                            "properties": {
                                "@id": {"type": "keyword"},
                                "type": {"type": "keyword"},
                                "published": {"type": "date"},
                                "prov:wasGeneratedBy": {"type": "keyword"},
                                "prov:generatedAtTime": {"type": "date"},
                                "prov:wasInformedBy": {
                                    "type": "nested",
                                    "properties": {
                                        "type": {"type": "keyword"},
                                        "id": {"type": "keyword"}
                                    }
                                },
                                "actor": {
                                    "properties": {
                                        "type": {"type": "keyword"},
                                        "id": {"type": "keyword"},
                                        "name": {"type": "text"},
                                        "url": {"type": "keyword"}
                                    }
                                },
                                "object": {
                                    "properties": {
                                        "totalItems": {"type": "integer"},
                                        "type": {"type": "keyword"},
                                        "items": {
                                            "type": "nested",
                                            "properties": {
                                                "type": {"type": "keyword"},
                                                "href": {"type": "keyword"},
                                                "OriginalResource": {
                                                    "type": "keyword"
                                                }
                                            }
                                        }
                                    }
                                },
                                "target": {
                                    "properties": {
                                        "id": {"type": "keyword"},
                                        "name": {"type": "keyword"},
                                        "type": {"type": "keyword"}
                                    }
                                },
                                "result": {
                                    "properties": {
                                        "totalItems": {"type": "integer"},
                                        "type": {"type": "keyword"},
                                        "items": {
                                            "type": "nested",
                                            "properties": {
                                                "type": {"type": "keyword"},
                                                "href": {"type": "keyword"},
                                                "OriginalResource": {
                                                    "type": "keyword"
                                                },
                                                "Memento": {"type": "keyword"},
                                                "mementoDatetime": {
                                                    "type": "keyword"
                                                },
                                                "location": {"type": "keyword"}
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        "activity": {
                            "properties": {
                                "@id": {"type": "keyword"},
                                "type": {"type": "keyword"},
                                "prov:used": {"enabled": False}
                            }
                        }
                    }
                }
            }
        }
    }
}


def create(es: Elasticsearch):
    return es.indices.create(
        index=INDEX_NAME,
        body=MAPPING
    )


def exists(es: Elasticsearch):
    return es.indices.exists(
        index=INDEX_NAME
    )


def get(es: Elasticsearch):
    return es.indices.get(
        index=INDEX_NAME
    )


def get_mapping(es: Elasticsearch):
    return es.indices.get(
        index=INDEX_NAME
    )
