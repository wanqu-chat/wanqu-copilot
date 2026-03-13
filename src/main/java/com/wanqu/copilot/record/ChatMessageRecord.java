package com.wanqu.copilot.record;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonRawValue;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.ai.chat.messages.MessageType;

@Getter
@Setter
@TableName(value = "chat_message_record", autoResultMap = true)
public class ChatMessageRecord extends AbstractRecord {
  String conversationUid;
  MessageType messageType;
  String text;

  @JsonRawValue String toolCalls;

  @JsonRawValue String responses;

  @TableField(typeHandler = JacksonTypeHandler.class)
  Map<String, Object> metadata;
}
