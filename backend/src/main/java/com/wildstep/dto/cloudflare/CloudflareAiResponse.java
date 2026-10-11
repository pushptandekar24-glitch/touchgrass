package com.wildstep.dto.cloudflare;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudflareAiResponse {

    private Result result;
    private boolean success;
    private List<ErrorItem> errors;
    private List<String> messages;

    public CloudflareAiResponse() {
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<ErrorItem> getErrors() {
        return errors;
    }

    public void setErrors(List<ErrorItem> errors) {
        this.errors = errors;
    }

    public List<String> getMessages() {
        return messages;
    }

    public void setMessages(List<String> messages) {
        this.messages = messages;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private String response;

        public Result() {
        }

        public Result(String response) {
            this.response = response;
        }

        public String getResponse() {
            return response;
        }

        public void setResponse(String response) {
            this.response = response;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ErrorItem {
        private Integer code;
        private String message;

        public ErrorItem() {
        }

        public ErrorItem(Integer code, String message) {
            this.code = code;
            this.message = message;
        }

        public Integer getCode() {
            return code;
        }

        public void setCode(Integer code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
