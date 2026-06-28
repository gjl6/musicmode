package com.gjl.music.module.song.importtodb;

import com.gjl.music.editor.mapper.SongManageMapper;
import com.gjl.music.model.MusicMetadata;
import com.gjl.music.module.FailurePolicy;
import com.gjl.music.module.song.support.MetadataGapFillingModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 导入到数据库实现 —— 空壳，所有逻辑由 {@link MetadataGapFillingModule} 基类提供。
 *
 * <p>基类流程：resolveInputPaths → queryDbCache → isDataMissing
 * → invoke("parser") 补缺 → invoke("db-operator") 入库 → recordItemResult 上报。</p>
 */
@Slf4j
@Component
public class ImportToDbModuleImpl extends MetadataGapFillingModule implements ImportToDbModule {

    public ImportToDbModuleImpl(SongManageMapper songManageMapper) {
        super(songManageMapper);
    }

    @Override
    public String name() { return "import-to-db"; }

    @Override
    public FailurePolicy failurePolicy() { return FailurePolicy.FAIL_FAST; }

    @Override
    public boolean isUserVisible() { return false; }

    /** 导入模式不写文件标签，只入库。 */
    @Override
    protected boolean shouldWriteTags() { return false; }

    /** 不做额外处理，元数据原样通过。 */
    @Override
    protected MusicMetadata processItem(MusicMetadata meta) {
        return meta;
    }
}
