package com.wanqu.copilot.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanqu.copilot.record.ChatMessageRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageRecord> {

  default ChatMessageRecord selectByUid(String uid) {
    return selectOne(
        new LambdaQueryWrapper<ChatMessageRecord>().eq(ChatMessageRecord::getUid, uid));
  }
}
