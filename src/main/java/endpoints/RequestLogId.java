package endpoints;

import lombok.Value;

import java.io.Serializable;

@Value
public class RequestLogId implements Serializable {
    
    long id;
}
