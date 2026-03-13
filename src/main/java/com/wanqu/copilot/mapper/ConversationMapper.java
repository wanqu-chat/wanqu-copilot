package com.wanqu.copilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanqu.copilot.record.ConversationRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMapper extends BaseMapper<ConversationRecord> {}
