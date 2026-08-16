package com.cobre.notifications.adapter.in.web.monitoring;

import com.cobre.notifications.application.model.NotificationEventNotFoundException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice(assignableTypes = NotificationDeliveryMonitoringController.class)
public class NotificationDeliveryMonitoringApiExceptionHandler {

    @ExceptionHandler(NotificationEventNotFoundException.class)
    ProblemDetail handleNotFound(NotificationEventNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage());
        problem.setTitle("Notification event not found");
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleInvalidQuery(ConstraintViolationException exception) {
        boolean invalidReturnValue = exception.getConstraintViolations().stream()
                .anyMatch(violation -> containsReturnValue(violation.getPropertyPath()));
        if (invalidReturnValue) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Stored delivery history violates the application contract");
            problem.setTitle("Invalid delivery history");
            return problem;
        }

        String detail = exception.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .distinct()
                .sorted()
                .collect(Collectors.joining("; "));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Invalid investigation parameters");
        return problem;
    }

    private boolean containsReturnValue(Path propertyPath) {
        for (Path.Node node : propertyPath) {
            if (node.getKind() == ElementKind.RETURN_VALUE) {
                return true;
            }
        }
        return false;
    }
}
