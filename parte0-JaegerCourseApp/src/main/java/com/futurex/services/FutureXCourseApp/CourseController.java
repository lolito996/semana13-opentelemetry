package com.futurex.services.FutureXCourseApp;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

@RestController
public class CourseController {

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

    @Autowired
    private CourseRepository courseRepository;

    private final Tracer tracer;
    private final OpenTelemetry openTelemetry;

    public CourseController(Tracer tracer, OpenTelemetry openTelemetry) {
        this.tracer = tracer;
        this.openTelemetry = openTelemetry;
    }

    @RequestMapping("/")
    public String getCourseAppHome(HttpServletRequest request) {
        Span span = startServerSpan("course.home", request);
        try (Scope scope = span.makeCurrent()) {
            return "Course App Home";
        } catch (RuntimeException exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR);
            throw exception;
        } finally {
            span.end();
        }
    }

    @RequestMapping("/courses")
    public List<Course> getCourses(HttpServletRequest request) {
        Span span = startServerSpan("course.list", request);
        try (Scope scope = span.makeCurrent()) {
            List<Course> courses = courseRepository.findAll();
            span.setAttribute("course.count", courses.size());
            return courses;
        } catch (RuntimeException exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR);
            throw exception;
        } finally {
            span.end();
        }
    }

    @RequestMapping("/{id}")
    public Course getSpecificCourse(@PathVariable("id") BigInteger id, HttpServletRequest request) {
        Span span = startServerSpan("course.get-by-id", request);
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute("course.id", id.longValue());
            return courseRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Course not found: " + id));
        } catch (RuntimeException exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR);
            throw exception;
        } finally {
            span.end();
        }
    }

    @RequestMapping(method = RequestMethod.POST, value="/courses")
    public void saveCourse(@RequestBody Course course, HttpServletRequest request) {
        Span span = startServerSpan("course.save", request);
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute("course.id", course.getCourseid().longValue());
            courseRepository.save(course);
        } catch (RuntimeException exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR);
            throw exception;
        } finally {
            span.end();
        }
    }

    @RequestMapping(method = RequestMethod.DELETE,value = "{id}")
    public void deleteCourse(@PathVariable BigInteger id, HttpServletRequest request) {
        Span span = startServerSpan("course.delete", request);
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute("course.id", id.longValue());
            courseRepository.deleteById(id);
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
