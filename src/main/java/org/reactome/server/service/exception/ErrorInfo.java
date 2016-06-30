package org.reactome.server.service.exception;

import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.List;

/**
 * Created by:
 *
 * @author Florian Korninger (florian.korninger@ebi.ac.uk)
 * @since 18.05.16.
 */
public class ErrorInfo {

    public final int code;
    public final HttpStatus reason;
    public String[] message = null;

//    public ErrorInfo(HttpStatus status, StringBuffer url, List<String> message) {
//        this.status = status;
//        this.url = url.toString();
//        this.reason = status.getReasonPhrase();
//        this.message = message;
//    }

    public ErrorInfo(HttpStatus status, String... message) {
        this.code = status.value();
        this.reason = status;
        if (message != null && message.length > 0) {
            this.message = message;
        }
    }
}