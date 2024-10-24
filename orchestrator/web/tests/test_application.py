# -*- coding: utf-8 -*-

from tests import ResearcherPodTests
from researcher_pod.config import Config
import os


class TestApplication(ResearcherPodTests):

    def test_pod_secrets(self):
        secrets_filename = os.path.join(os.path.dirname(__file__),
                                        "../../secrets/secrets")
        os.environ["RESEARCHER_POD_SECRETS"] = secrets_filename

        secrets = self.pod.get_secrets()
        assert secrets is not None
        assert len(secrets) == 2
        assert isinstance(secrets[0], str)
        assert isinstance(secrets[1], str)

    def test_pod_config(self):
        config = Config()
        file_name = os.getenv("RESEARCHER_POD_CONFIG")

        # invalid file name
        with self.assertRaises(FileNotFoundError):
            config.from_file(os.path.join(
                os.path.dirname(os.path.realpath(__file__)),
                "config.yaml"))

        # valid config from file
        config.from_file(file_name)
        valid, msgs = config.validate()
        assert valid
        assert len(msgs) == 0

        import yaml
        with open(file_name, "rb") as f:
            y_conf = yaml.load(f)

        # forcing errors in portal parameters
        y_conf["portals"]["newportal"] = {}
        y_config = Config()
        y_config.from_object(y_conf)
        valid, msgs = y_config.validate()
        assert not valid
        assert len(msgs) >= 1
        assert "newportal" in "".join(msgs)

    def test_get_secrets(self):
        # get default secret
        key, salt = self.pod.get_secrets()
        assert key is not None
        assert salt is not None

        with self.assertRaises(FileNotFoundError):
            os.environ["RESEARCHER_POD_SECRETS"] = "bad_file"
            self.pod.get_secrets()

        # creating file with bad data
        with self.assertRaises(ValueError):
            import tempfile
            tf = tempfile.NamedTemporaryFile()
            tf.write(b"aa")
            os.environ["RESEARCHER_POD_SECRETS"] = tf.name
            self.pod.get_secrets()
