package com.cobre.notifications.adapter.in.web.notification;

import com.cobre.notifications.application.model.InvalidNotificationEventQueryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = NotificationEventController.class)
public class NotificationEventApiExceptionHandler {

    @ExceptionHandler(InvalidNotificationEventQueryException.class)
    ProblemDetail handleInvalidQuery(InvalidNotificationEventQueryException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid request parameters");
        return problem;
    }
}
