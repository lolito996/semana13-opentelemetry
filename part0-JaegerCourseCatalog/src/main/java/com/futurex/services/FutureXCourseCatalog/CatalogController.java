package com.futurex.services.FutureXCourseCatalog;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

@RestController
public class CatalogController {

    private static final TextMapGetter<HttpServletRequest> REQUEST_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(HttpServletRequest carrier) {
            return Collections.list(carrier.getHeaderNames());
        }

        @Override
        public String get(HttpServletRequest carrier, String key) {
            return carrier.getHeader(key);
        }
    };

    @Value("${course.service.url}")
    private String courseServiceUrl;

    private final RestTemplate restTemplate;
    private final Tracer tracer;
    private final OpenTelemetry openTelemetry;

    public CatalogController(RestTemplate restTemplate, Tracer tracer, OpenTelemetry openTelemetry) {
        this.restTemplate = restTemplate;
        this.tracer = tracer;
        this.openTelemetry = openTelemetry;
    }

    @RequestMapping("/")
    public String getCatalogHome(HttpServletRequest request) {
        Span span = startServerSpan("catalog.home", request);
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute("course.service.url", courseServiceUrl);
            String courseAppMessage = restTemplate.getForObject(courseServiceUrl, String.class);
            return "Welcome to FutureX Course Catalog " + courseAppMessage;
        } catch (RuntimeException exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR);
            throw exception;
        } finally {
            span.end();
        }
    }

    @RequestMapping("/catalog")
    public String getCatalog(HttpServletRequest request) {
        Span span = startServerSpan("catalog.list", request);
        try (Scope scope = span.makeCurrent()) {
            String targetUrl = courseServiceUrl + "/courses";
            span.setAttribute("course.service.url", targetUrl);
            String courses = restTemplate.getForObject(targetUrl, String.class);
            return "Our courses are " + courses;
        } catch (RuntimeException exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR);
            throw exception;
        } finally {
            span.end();
        }
    }

    @RequestMapping("/firstcourse")
    public String getSpecificCourse(HttpServletRequest request) {
        Span span = startServerSpan("catalog.first-course", request);
        try (Scope scope = span.makeCurrent()) {
            String targetUrl = courseServiceUrl + "/1";
            span.setAttribute("course.service.url", targetUrl);
            Course course = restTemplate.getForObject(targetUrl, Course.class);
            return "Our first course is " + course.getCoursename();
        } catch (RuntimeException exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR);
            throw exception;
        } finally {
            span.end();
        }
    }

    private Span startServerSpan(String spanName, HttpServletRequest request) {
        Context extractedContext = openTelemetry.getPropagators()
                .getTextMapPropagator()
                .extract(Context.current(), request, REQUEST_GETTER);

        return tracer.spanBuilder(spanName)
                .setParent(extractedContext)
                .setSpanKind(SpanKind.SERVER)
                .startSpan();
    }
}
