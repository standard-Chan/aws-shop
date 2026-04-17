package jeong.awsshop.common.exception;

public class JsonParsingException extends RuntimeException {

    public JsonParsingException(String message, Throwable cause) {
        super("[JSON 파싱 실패]: " + message, cause);
    }
}
