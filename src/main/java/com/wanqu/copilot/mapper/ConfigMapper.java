package com.wanqu.copilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanqu.copilot.record.ConfigRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConfigMapper extends BaseMapper<ConfigRecord> {}
