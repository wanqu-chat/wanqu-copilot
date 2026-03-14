package com.wanqu.copilot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wanqu.copilot.record.ProjectRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectMapper extends BaseMapper<ProjectRecord> {}
