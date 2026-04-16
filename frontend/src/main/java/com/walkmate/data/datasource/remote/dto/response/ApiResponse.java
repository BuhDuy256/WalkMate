package com.walkmate.data.datasource.remote.dto.response;

public class ApiResponse<T> {

    private boolean  success;
    private T        data;
    private ApiError error;

    /** Empty constructor for Gson. */
    public ApiResponse() {}

    public boolean isSuccess()   { return success; }
    public boolean getSuccess()  { return success; }
    public T       getData()     { return data; }
    public ApiError getError()   { return error; }
}
