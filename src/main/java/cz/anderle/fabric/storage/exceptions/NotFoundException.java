package cz.anderle.fabric.storage.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, UUID id) {
        super("%s with ID %s not found".formatted(resource, id));
    }

    public NotFoundException(String msg, Object... args) {
        super(String.format(msg, args));
    }
}
