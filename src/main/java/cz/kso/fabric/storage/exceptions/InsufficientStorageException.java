package cz.kso.fabric.storage.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INSUFFICIENT_STORAGE)
public class InsufficientStorageException extends RuntimeException {
    public InsufficientStorageException() {
        super();
    }
}
