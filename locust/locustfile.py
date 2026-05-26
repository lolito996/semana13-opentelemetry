import os
import random
import time
from html import escape

from flask import Response
from locust import HttpUser, between, events, task


COURSE_SERVICE_URL = os.getenv("COURSE_SERVICE_URL", "http://host.docker.internal:9001").rstrip("/")
CATALOG_SERVICE_URL = os.getenv("CATALOG_SERVICE_URL", "http://host.docker.internal:9002").rstrip("/")


def _metric_label(value):
    return str(value).replace("\\", "\\\\").replace("\n", "\\n").replace('"', '\\"')


def _sample(metric, value, labels=None):
    labels = labels or {}
    if labels:
        label_text = ",".join(f'{key}="{_metric_label(val)}"' for key, val in labels.items())
        return f"{metric}{{{label_text}}} {value}"
    return f"{metric} {value}"


@events.init.add_listener
def expose_prometheus_metrics(environment, **_kwargs):
    if not environment.web_ui:
        return

    @environment.web_ui.app.route("/metrics")
    def metrics():
        stats = environment.stats
        lines = [
            "# HELP locust_users Number of active Locust users.",
            "# TYPE locust_users gauge",
            _sample("locust_users", environment.runner.user_count if environment.runner else 0),
            "# HELP locust_requests_total Total number of Locust requests.",
            "# TYPE locust_requests_total counter",
            "# HELP locust_failures_total Total number of failed Locust requests.",
            "# TYPE locust_failures_total counter",
            "# HELP locust_current_rps Current requests per second observed by Locust.",
            "# TYPE locust_current_rps gauge",
            "# HELP locust_current_fail_per_sec Current failures per second observed by Locust.",
            "# TYPE locust_current_fail_per_sec gauge",
            "# HELP locust_response_time_avg Average response time in milliseconds.",
            "# TYPE locust_response_time_avg gauge",
            "# HELP locust_response_time_min Minimum response time in milliseconds.",
            "# TYPE locust_response_time_min gauge",
            "# HELP locust_response_time_max Maximum response time in milliseconds.",
            "# TYPE locust_response_time_max gauge",
            "# HELP locust_response_time_percentile Response time percentile in milliseconds.",
            "# TYPE locust_response_time_percentile gauge",
        ]

        for entry in stats.entries.values():
            labels = {"method": entry.method, "name": entry.name}
            lines.extend(
                [
                    _sample("locust_requests_total", entry.num_requests, labels),
                    _sample("locust_failures_total", entry.num_failures, labels),
                    _sample("locust_current_rps", entry.current_rps, labels),
                    _sample("locust_current_fail_per_sec", entry.current_fail_per_sec, labels),
                    _sample("locust_response_time_avg", entry.avg_response_time, labels),
                    _sample("locust_response_time_min", entry.min_response_time or 0, labels),
                    _sample("locust_response_time_max", entry.max_response_time, labels),
                ]
            )
            for percentile in (0.5, 0.75, 0.9, 0.95, 0.99):
                lines.append(
                    _sample(
                        "locust_response_time_percentile",
                        entry.get_response_time_percentile(percentile),
                        {**labels, "percentile": percentile},
                    )
                )

        lines.extend(
            [
                "# HELP locust_state Current Locust run state as an info metric.",
                "# TYPE locust_state gauge",
                _sample(
                    "locust_state",
                    1,
                    {"state": environment.runner.state if environment.runner else "not_ready"},
                ),
            ]
        )
        return Response("\n".join(lines) + "\n", mimetype="text/plain; version=0.0.4")

    @environment.web_ui.app.route("/futurex-targets")
    def targets():
        body = (
            "<h1>FutureX Locust Targets</h1>"
            f"<p>Course service: <code>{escape(COURSE_SERVICE_URL)}</code></p>"
            f"<p>Catalog service: <code>{escape(CATALOG_SERVICE_URL)}</code></p>"
        )
        return Response(body, mimetype="text/html")


class FutureXUser(HttpUser):
    wait_time = between(0.5, 2.5)
    host = CATALOG_SERVICE_URL

    def on_start(self):
        self.course_ids = [1, 2, 3]

    @task(8)
    def catalog_courses(self):
        self.client.get(f"{CATALOG_SERVICE_URL}/catalog", name="catalog /catalog")

    @task(5)
    def first_course_from_catalog(self):
        self.client.get(f"{CATALOG_SERVICE_URL}/firstcourse", name="catalog /firstcourse")

    @task(3)
    def catalog_home(self):
        self.client.get(f"{CATALOG_SERVICE_URL}/", name="catalog /")

    @task(6)
    def course_list(self):
        self.client.get(f"{COURSE_SERVICE_URL}/courses", name="course /courses")

    @task(4)
    def course_detail(self):
        course_id = random.choice(self.course_ids)
        self.client.get(f"{COURSE_SERVICE_URL}/{course_id}", name="course /{id}")

    @task(1)
    def create_course(self):
        course_id = int(time.time() * 1000) + random.randint(1, 999)
        payload = {
            "courseid": course_id,
            "coursename": f"Locust Course {course_id}",
            "author": "Locust",
        }
        self.client.post(f"{COURSE_SERVICE_URL}/courses", json=payload, name="course POST /courses")
