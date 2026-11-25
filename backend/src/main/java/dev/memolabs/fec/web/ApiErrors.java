package dev.memolabs.fec.web;

import dev.memolabs.fec.core.FecFormatException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Turns a file that cannot be read into a 400 with a message the user can act on. */
@RestControllerAdvice
public class ApiErrors {

    @ExceptionHandler(FecFormatException.class)
    public ProblemDetail unreadableFile(FecFormatException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Fichier illisible");
        return problem;
    }

    @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class})
    public ProblemDetail missingFile(Exception e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Il faut envoyer deux fichiers : oldFec (ancien FEC) et newFec (nouveau FEC).");
        problem.setTitle("Fichier manquant");
        return problem;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail tooLarge(MaxUploadSizeExceededException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
                "Un fichier dépasse 200 Mo, la limite de ce prototype.");
        problem.setTitle("Fichier trop gros");
        return problem;
    }
}
