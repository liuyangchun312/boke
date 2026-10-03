package com.example.blog.comment.dto;

import com.example.blog.comment.model.CommentStatus;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;

import java.io.IOException;

public record CommentStatusRequest(
        @NotNull @JsonDeserialize(using = CommentStatusRequest.StatusDeserializer.class) CommentStatus status) {
    public static final class StatusDeserializer extends JsonDeserializer<CommentStatus> {
        @Override
        public CommentStatus deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return (CommentStatus) context.handleUnexpectedToken(CommentStatus.class, parser);
            }
            try {
                return CommentStatus.valueOf(parser.getText());
            } catch (IllegalArgumentException ex) {
                return (CommentStatus) context.handleWeirdStringValue(CommentStatus.class, parser.getText(),
                        "Invalid comment status");
            }
        }
    }
}
