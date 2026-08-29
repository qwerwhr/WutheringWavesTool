package cn.tealc.wwt.app.update;

public class UpdateResult<T> {
    private final int code;
    private final String message;
    private final T data;

    public UpdateResult(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> UpdateResult<T> success(T data, String message) {
        return new UpdateResult<>(200, message, data);
    }

    public static <T> UpdateResult<T> noUpdate(String message) {
        return new UpdateResult<>(1, message, null);
    }

    public static <T> UpdateResult<T> error(int code, String message) {
        return new UpdateResult<>(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public boolean isSuccess() {
        return code == 200;
    }

    public boolean hasUpdate() {
        return code == 200;
    }
}