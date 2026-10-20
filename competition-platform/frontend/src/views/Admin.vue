<template>
  <div>
    <el-card shadow="never">
      <el-tabs v-model="tab">
        <el-tab-pane label="竞赛管理" name="competition">
          <div class="card-head">
            <el-button type="primary" size="small" @click="addVisible = true"
              >＋ 新增竞赛</el-button
            >
          </div>
          <el-table
            :data="list"
            size="small"
            v-loading="loading"
            style="margin-top: 12px"
          >
            <el-table-column prop="name" label="竞赛名称" min-width="160" />
            <el-table-column prop="organizer" label="主办方" min-width="140" />
            <el-table-column label="级别" width="80">
              <template #default="{ row }"
                ><el-tag size="small">{{ row.level }}</el-tag></template
              >
            </el-table-column>
            <el-table-column prop="signupStart" label="报名开始" width="160" />
            <el-table-column prop="signupEnd" label="报名截止" width="160" />
            <el-table-column prop="startTime" label="比赛开始" width="160" />
            <el-table-column label="状态" min-width="185">
              <template #default="{ row }">
                <div class="competition-tags">
                  <el-tag
                    :type="COMPETITION_STATUS_TYPE[row.status] || 'info'"
                    size="small"
                    >{{ competitionStatusLabel(row.status) }}</el-tag
                  >
                  <el-tag
                    v-if="isCompetitionStartingSoon(row)"
                    type="danger"
                    size="small"
                    effect="plain"
                    >即将开赛</el-tag
                  >
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button type="danger" size="small" link @click="remove(row)"
                  >删除</el-button
                >
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="赛事发现" name="discovery">
          <el-alert
            v-if="discoveryStatus"
            class="discovery-status"
            :type="discoveryStatus.searchConfigured ? 'success' : 'warning'"
            :closable="false"
            show-icon
            :title="discoveryStatus.searchStatus"
            :description="`模型：${discoveryStatus.modelStatus}；单次抓取超时 ${discoveryStatus.fetchTimeoutSeconds} 秒，单次最多 ${discoveryStatus.maxResults} 条，整项作业上限 ${discoveryStatus.jobTimeoutSeconds || 300} 秒。`"
          />
          <el-alert
            v-else
            class="discovery-status"
            type="info"
            :closable="false"
            show-icon
            title="正在读取搜索与模型配置状态"
          />

          <el-card shadow="never" class="inner-card">
            <el-form label-width="92px" size="small">
              <el-form-item label="来源方式">
                <el-radio-group v-model="discoveryForm.mode">
                  <el-radio-button label="SEARCH">联网搜索</el-radio-button>
                  <el-radio-button label="URL">官方公告 URL</el-radio-button>
                  <el-radio-button label="TEXT">粘贴公告文本</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <template v-if="discoveryForm.mode === 'SEARCH'">
                <el-form-item label="关键词">
                  <el-input v-model="discoveryForm.keyword" placeholder="如：大学生数学建模竞赛" />
                </el-form-item>
                <el-form-item label="方向 / 范围">
                  <el-input v-model="discoveryForm.direction" placeholder="如：算法、创新创业" style="width: 45%" />
                  <el-input v-model="discoveryForm.region" placeholder="地区/参赛范围，可空" style="width: 45%; margin-left: 10px" />
                </el-form-item>
                <el-form-item label="时间窗口">
                  <el-input-number v-model="discoveryForm.windowDays" :min="1" :max="365" controls-position="right" />
                  <span class="inline-hint">天；结果最多</span>
                  <el-input-number v-model="discoveryForm.maxResults" :min="1" :max="10" controls-position="right" style="margin-left: 8px" />
                  <span class="inline-hint">条</span>
                </el-form-item>
                <el-alert
                  v-if="discoveryStatus && !discoveryStatus.searchConfigured"
                  type="warning"
                  :closable="false"
                  show-icon
                  title="搜索 API 未配置：可改用 URL 或粘贴公告文本，不能宣称联网搜索已成功"
                />
              </template>
              <template v-else-if="discoveryForm.mode === 'URL'">
                <el-form-item label="公告 URL">
                  <el-input v-model="discoveryForm.url" placeholder="https://..." />
                </el-form-item>
                <el-alert
                  type="info"
                  :closable="false"
                  show-icon
                  title="只抓取一次公网页面；不会跟随到内网，也不会把网页“最后更新”当作赛事年份"
                />
              </template>
              <template v-else>
                <el-form-item label="公告标题">
                  <el-input v-model="discoveryForm.pageTitle" placeholder="可空，默认“管理员粘贴的公告文本”" />
                </el-form-item>
                <el-form-item label="公告正文">
                  <el-input v-model="discoveryForm.text" type="textarea" :rows="6" maxlength="20000" show-word-limit placeholder="粘贴官方公告正文；系统会记录为人工提供材料，不代表已核验网页" />
                </el-form-item>
              </template>
              <el-form-item>
                <el-button type="primary" :loading="discoveryLoading" @click="startDiscovery">
                  启动发现作业
                </el-button>
                <span class="inline-hint">搜索只负责找网页；必须取回正文才会生成草稿。</span>
              </el-form-item>
            </el-form>
          </el-card>

          <el-card v-if="job" shadow="never" class="inner-card">
            <div class="job-head">
              <span>作业 #{{ job.id || '—' }}</span>
              <el-tag size="small" :type="jobStatusMeta(job.status).type">{{ jobStatusMeta(job.status).label }}</el-tag>
            </div>
            <el-progress :percentage="jobProgress(job)" :status="job.status === 'FAILED' ? 'exception' : undefined" />
            <p class="job-message">{{ job.message }}</p>
            <el-alert
              v-if="job.result?.truncated"
              class="discovery-status"
              type="warning"
              :closable="false"
              show-icon
              :title="`作业达到 ${job.result?.timeoutSeconds || 300} 秒总时限，部分结果未处理；可减少条数后重试`"
            />
            <div v-if="job.result?.failures?.length">
              <h4>失败项</h4>
              <el-table :data="job.result.failures" size="small">
                <el-table-column prop="url" label="URL" min-width="220" show-overflow-tooltip />
                <el-table-column prop="code" label="错误码" width="150" />
                <el-table-column prop="message" label="说明" min-width="220" />
              </el-table>
            </div>
            <div v-if="job.result?.duplicates?.length">
              <h4>重复/已存在</h4>
              <el-table :data="job.result.duplicates" size="small">
                <el-table-column prop="name" label="名称" min-width="160" />
                <el-table-column prop="duplicateType" label="类型" width="110" />
                <el-table-column prop="duplicateId" label="现有 ID" width="90" />
                <el-table-column prop="message" label="说明" min-width="180" />
              </el-table>
            </div>
          </el-card>

          <div class="draft-head">
            <div>
              <strong>赛事草稿</strong>
              <span class="inline-hint">草稿不会出现在学生端；发布前必须补齐来源与关键字段。</span>
            </div>
            <div>
              <el-select v-model="draftStatus" size="small" style="width: 120px" @change="loadDrafts">
                <el-option label="草稿" value="DRAFT" />
                <el-option label="待发布" value="READY" />
                <el-option label="已发布" value="PUBLISHED" />
                <el-option label="已拒绝" value="REJECTED" />
              </el-select>
              <el-button size="small" style="margin-left: 8px" @click="loadDrafts">刷新</el-button>
            </div>
          </div>
          <el-table :data="drafts" size="small" v-loading="draftLoading">
            <el-table-column prop="name" label="赛事名称" min-width="180" show-overflow-tooltip />
            <el-table-column prop="organizer" label="主办方" min-width="130" show-overflow-tooltip />
            <el-table-column label="报名截止" width="160">
              <template #default="{ row }">{{ formatDateTime(row.signupEnd) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="draftStatusMeta(row.status).type">{{ draftStatusMeta(row.status).label }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="extractionMode" label="提取" width="70" />
            <el-table-column label="更新时间" width="160">
              <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openDraft(row)">查看/审核</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!drafts.length && !draftLoading" description="暂无该状态草稿" :image-size="60" />
        </el-tab-pane>

        <el-tab-pane label="反馈管理" name="feedback">
          <el-table :data="feedbacks" size="small" v-loading="fbLoading">
            <el-table-column prop="userName" label="提交人" width="90" />
            <el-table-column prop="type" label="类型" width="100" />
            <el-table-column prop="content" label="内容" min-width="260" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag
                  :type="row.status === '待处理' ? 'warning' : 'success'"
                  size="small"
                  >{{ row.status }}</el-tag
                >
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="提交时间" width="170" />
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === '待处理'"
                  type="success"
                  size="small"
                  link
                  @click="handleFeedback(row)"
                  >标记已处理</el-button
                >
                <span v-else style="color: #909399; font-size: 12px"
                  >已处理</span
                >
              </template>
            </el-table-column>
          </el-table>
          <el-empty
            v-if="!feedbacks.length"
            description="暂无反馈"
            :image-size="60"
          />
          <el-pagination
            v-if="fbTotal > fbPageSize"
            layout="prev, pager, next, total"
            :total="fbTotal"
            :page-size="fbPageSize"
            :current-page="fbPage"
            style="justify-content: center; margin-top: 8px"
            @current-change="onFbPageChange"
          />
        </el-tab-pane>

        <el-tab-pane label="操作日志" name="logs">
          <el-table :data="logs" size="small" v-loading="logLoading">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="userName" label="操作人" width="90" />
            <el-table-column prop="action" label="动作" width="110" />
            <el-table-column prop="detail" label="详情" min-width="220" />
            <el-table-column prop="createTime" label="时间" width="170" />
          </el-table>
          <el-empty
            v-if="!logs.length"
            description="暂无操作日志"
            :image-size="60"
          />
          <el-pagination
            v-if="logTotal > logPageSize"
            layout="prev, pager, next, total"
            :total="logTotal"
            :page-size="logPageSize"
            :current-page="logPage"
            style="justify-content: center; margin-top: 8px"
            @current-change="onLogPageChange"
          />
        </el-tab-pane>

        <el-tab-pane label="成果审核" name="achievement">
          <div style="margin-bottom: 12px">
            <el-radio-group
              v-model="achStatus"
              size="small"
              @change="loadAchievements"
            >
              <el-radio-button label="待审核">待审核</el-radio-button>
              <el-radio-button label="已通过">已通过</el-radio-button>
              <el-radio-button label="已拒绝">已拒绝</el-radio-button>
            </el-radio-group>
          </div>
          <el-table :data="achList" size="small" v-loading="achLoading">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="userName" label="发布人" width="90" />
            <el-table-column prop="name" label="成果名称" min-width="150" />
            <el-table-column prop="competitionName" label="竞赛" width="130" />
            <el-table-column prop="award" label="获奖情况" min-width="110" />
            <el-table-column label="证明" width="90">
              <template #default="{ row }">
                <el-link
                  type="primary"
                  :href="row.proof"
                  target="_blank"
                  :underline="false"
                  >查看</el-link
                >
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <template v-if="row.status === '待审核'">
                  <el-button
                    type="success"
                    size="small"
                    link
                    @click="reviewAchievement(row, 'approve')"
                    >通过</el-button
                  >
                  <el-button
                    type="danger"
                    size="small"
                    link
                    @click="reviewAchievement(row, 'reject')"
                    >拒绝</el-button
                  >
                </template>
                <el-button
                  v-else
                  type="danger"
                  size="small"
                  link
                  @click="removeAchievement(row)"
                  >删除</el-button
                >
              </template>
            </el-table-column>
          </el-table>
          <el-empty
            v-if="!achList.length"
            description="暂无成果"
            :image-size="60"
          />
          <el-pagination
            v-if="achTotal > achPageSize"
            layout="prev, pager, next, total"
            :total="achTotal"
            :page-size="achPageSize"
            :current-page="achPage"
            style="justify-content: center; margin-top: 8px"
            @current-change="onAchPageChange"
          />
        </el-tab-pane>

        <el-tab-pane label="训练资源" name="training">
          <div style="margin-bottom: 12px">
            <el-button type="primary" size="small" @click="openSiteAdd"
              >＋ 新增训练资源</el-button
            >
            <el-input
              v-model="siteKeyword"
              placeholder="搜索资源名"
              clearable
              style="width: 200px; margin-left: 10px"
              @keyup.enter="loadSites"
            />
          </div>
          <el-table :data="sites" size="small" v-loading="siteLoading">
            <el-table-column prop="name" label="资源名" min-width="150" />
            <el-table-column label="直达" width="80">
              <template #default="{ row }">
                <el-link
                  type="primary"
                  :href="row.url"
                  target="_blank"
                  :underline="false"
                  >打开</el-link
                >
              </template>
            </el-table-column>
            <el-table-column prop="competition" label="竞赛" width="90" />
            <el-table-column prop="tags" label="标签" width="130" />
            <el-table-column prop="difficulty" label="难度" width="80" />
            <el-table-column label="推荐" width="70">
              <template #default="{ row }"
                ><el-tag v-if="row.recommended" size="small" type="success"
                  >推荐</el-tag
                ><span v-else>—</span></template
              >
            </el-table-column>
            <el-table-column prop="status" label="状态" width="70" />
            <el-table-column label="操作" width="130">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  size="small"
                  link
                  @click="openSiteEdit(row)"
                  >编辑</el-button
                >
                <el-button
                  type="danger"
                  size="small"
                  link
                  @click="removeSite(row)"
                  >删除</el-button
                >
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-if="siteTotal > sitePageSize"
            layout="prev, pager, next, total"
            :total="siteTotal"
            :page-size="sitePageSize"
            :current-page="sitePage"
            style="justify-content: center; margin-top: 8px"
            @current-change="onSitePageChange"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="addVisible" title="新增竞赛" width="520px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称" required
          ><el-input v-model="form.name"
        /></el-form-item>
        <el-form-item label="主办方"
          ><el-input v-model="form.organizer"
        /></el-form-item>
        <el-form-item label="级别">
          <el-select v-model="form.level" style="width: 100%">
            <el-option v-for="l in LEVELS" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
        <el-form-item label="报名开始"
          ><el-date-picker
            v-model="form.signupStart"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
        /></el-form-item>
        <el-form-item label="报名截止"
          ><el-date-picker
            v-model="form.signupEnd"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
        /></el-form-item>
        <el-form-item label="比赛时间"
          ><el-date-picker
            v-model="form.startTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
        /></el-form-item>
        <el-form-item label="简介"
          ><el-input v-model="form.description" type="textarea" :rows="3"
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doAdd"
          >保存</el-button
        >
      </template>
    </el-dialog>

    <el-dialog v-model="draftDialog" title="赛事草稿审核" width="920px" top="5vh">
      <template v-if="draftDetail">
        <div class="draft-title-row">
          <div>
            <el-tag size="small" :type="draftStatusMeta(draftDetail.status).type">{{ draftStatusMeta(draftDetail.status).label }}</el-tag>
            <span class="draft-version">版本 {{ draftDetail.version }}</span>
            <el-tag size="small" effect="plain">{{ draftDetail.extractionMode || '—' }}</el-tag>
            <el-tag size="small" effect="plain">{{ draftDetail.sourceType || '—' }}</el-tag>
          </div>
          <el-link v-if="draftDetail.publishedCompetitionId" type="success" :underline="false" @click="router.push(`/competitions/${draftDetail.publishedCompetitionId}`)">
            查看已发布竞赛 #{{ draftDetail.publishedCompetitionId }}
          </el-link>
        </div>

        <el-alert
          v-if="draftDetail.staleReason"
          type="error"
          :closable="false"
          show-icon
          :title="draftDetail.staleReason"
          class="draft-alert"
        />
        <el-alert
          v-if="draftDetail.missingFields?.length"
          type="warning"
          :closable="false"
          show-icon
          :title="'缺失字段：' + draftDetail.missingFields.join('、')"
          class="draft-alert"
        />
        <el-alert
          v-if="draftDetail.conflicts?.length"
          type="warning"
          :closable="false"
          show-icon
          title="日期/来源冲突（需在审核备注说明处理结果）"
          class="draft-alert"
        >
          <ul><li v-for="item in draftDetail.conflicts" :key="item">{{ item }}</li></ul>
        </el-alert>
        <el-alert
          v-if="draftDetail.warnings?.length"
          type="info"
          :closable="false"
          show-icon
          title="提取提示"
          class="draft-alert"
        >
          <ul><li v-for="item in draftDetail.warnings" :key="item">{{ item }}</li></ul>
        </el-alert>
        <el-alert
          v-if="draftDetail.duplicateCandidates?.length"
          type="warning"
          :closable="false"
          show-icon
          title="疑似重复赛事，请核对后再确认"
          class="draft-alert"
        >
          <div v-for="item in draftDetail.duplicateCandidates" :key="item.type + item.id" class="duplicate-row">
            <el-tag size="small" effect="plain">{{ item.type }}</el-tag>
            <span>{{ item.name }}（{{ item.organizer || '主办方未知' }}，{{ item.year || '年份未知' }}，{{ item.track || '无赛道' }}）</span>
            <span class="inline-hint">{{ item.reason }}，相似度 {{ item.similarity }}%</span>
            <el-link v-if="item.type === 'COMPETITION'" type="primary" :underline="false" @click="router.push(`/competitions/${item.id}`)">查看</el-link>
          </div>
        </el-alert>
        <el-alert
          v-if="draftDetail.reviewNote"
          type="success"
          :closable="false"
          show-icon
          :title="'审核备注：' + draftDetail.reviewNote"
          class="draft-alert"
        />

        <el-form label-width="88px" size="small" class="draft-form">
          <el-row :gutter="12">
            <el-col :span="12"><el-form-item label="赛事名称"><el-input v-model="draftForm.name" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="主办方"><el-input v-model="draftForm.organizer" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="年份"><el-input-number v-model="draftForm.year" :min="0" :max="2100" controls-position="right" style="width: 100%" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="届次"><el-input v-model="draftForm.edition" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="赛道"><el-input v-model="draftForm.track" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="级别"><el-input v-model="draftForm.level" placeholder="校级/省级/国家级" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="方向标签"><el-input v-model="draftForm.tags" placeholder="逗号分隔" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="截止精度">
              <el-select v-model="draftForm.signupEndPrecision" style="width: 100%">
                <el-option label="日期+时间" value="DATETIME" />
                <el-option label="仅日期" value="DATE" />
                <el-option label="未知" value="UNKNOWN" />
              </el-select>
            </el-form-item></el-col>
            <el-col :span="8"><el-form-item label="报名开始"><el-date-picker v-model="draftForm.signupStart" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" /></el-form-item></el-col>
            <el-col :span="8">
              <el-form-item label="报名截止">
                <el-date-picker
                  v-if="draftForm.signupEndPrecision === 'DATE'"
                  v-model="draftForm.signupEnd"
                  type="date"
                  value-format="YYYY-MM-DD"
                  style="width: 100%"
                />
                <el-date-picker v-else v-model="draftForm.signupEnd" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8"><el-form-item label="开赛时间"><el-date-picker v-model="draftForm.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" /></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="参赛条件"><el-input v-model="draftForm.eligibilityText" type="textarea" :rows="2" /></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="简介"><el-input v-model="draftForm.description" type="textarea" :rows="3" /></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="日期说明"><el-input v-model="draftForm.dateNote" /></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="审核备注"><el-input v-model="draftForm.reviewNote" type="textarea" :rows="2" placeholder="记录来源、冲突处理或人工粘贴材料的出处" /></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="重复说明"><el-input v-model="draftForm.duplicateNote" placeholder="若确认是新届次/新赛道，请说明与现有赛事的差异" /></el-form-item></el-col>
            <el-col :span="24">
              <el-form-item label="确认项">
                <el-checkbox v-model="draftForm.confirmDateInterpretation">确认只有日期的报名截止按北京时间当日 23:59:59 解释</el-checkbox>
                <el-checkbox v-model="draftForm.duplicateAcknowledged">已核对疑似重复赛事并确认差异</el-checkbox>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <div class="evidence-block">
          <h4>字段证据</h4>
          <el-table :data="evidenceRows(draftDetail)" size="small" v-if="evidenceRows(draftDetail).length">
            <el-table-column prop="label" label="字段" width="110" />
            <el-table-column prop="value" label="正文原文片段" min-width="420" />
          </el-table>
          <p v-else class="inline-hint">没有可从正文核对的证据片段；人工粘贴文本请保留出处说明。</p>
        </div>

        <div class="evidence-block">
          <h4>来源记录</h4>
          <el-table :data="draftDetail.sources" size="small">
            <el-table-column prop="sourceType" label="类型" width="110" />
            <el-table-column prop="fetchStatus" label="抓取" width="90" />
            <el-table-column label="URL" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">
                <el-link v-if="row.url" type="primary" :href="row.url" target="_blank" :underline="false">{{ row.url }}</el-link>
                <span v-else>人工粘贴（无 URL）</span>
              </template>
            </el-table-column>
            <el-table-column label="抓取时间" width="160">
              <template #default="{ row }">{{ formatDateTime(row.fetchedAt) }}</template>
            </el-table-column>
          </el-table>
        </div>

        <el-alert
          v-if="publishBlockers(draftDetail).length"
          type="error"
          :closable="false"
          show-icon
          title="发布阻塞项"
          class="draft-alert"
        >
          <ul><li v-for="item in publishBlockers(draftDetail)" :key="item">{{ item }}</li></ul>
        </el-alert>
        <el-collapse v-if="draftDetail.sources?.some((s) => s.rawText)">
          <el-collapse-item title="查看实际取回/粘贴的正文" name="raw">
            <pre class="raw-text">{{ draftDetail.sources.find((s) => s.rawText)?.rawText }}</pre>
          </el-collapse-item>
        </el-collapse>
      </template>
      <template #footer>
        <el-button @click="draftDialog = false">关闭</el-button>
        <template v-if="draftDetail && draftDetail.status !== 'PUBLISHED'">
          <el-button :loading="draftSaving" @click="saveDraft('SAVE')">保存草稿</el-button>
          <el-button type="danger" plain :loading="draftSaving" @click="rejectDraft">拒绝</el-button>
          <el-button type="warning" :loading="draftSaving" @click="saveDraft('MARK_READY')">标记待发布</el-button>
          <el-button v-if="draftDetail.status === 'READY'" type="primary" :loading="draftSaving" @click="publishDraft">发布到学生端</el-button>
        </template>
      </template>
    </el-dialog>

    <el-dialog
      v-model="siteVisible"
      :title="siteForm.id ? '编辑训练资源' : '新增训练资源'"
      width="520px"
    >
      <el-form :model="siteForm" label-width="80px">
        <el-form-item label="资源名" required
          ><el-input v-model="siteForm.name" placeholder="如：力扣 LeetCode"
        /></el-form-item>
        <el-form-item label="链接" required
          ><el-input v-model="siteForm.url" placeholder="https://..."
        /></el-form-item>
        <el-form-item label="所属竞赛">
          <el-select v-model="siteForm.competition" style="width: 100%">
            <el-option
              v-for="c in TRAINING_COMPETITIONS"
              :key="c"
              :label="c"
              :value="c"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="技能标签">
          <el-input
            v-model="siteForm.tags"
            placeholder="逗号分隔，如：算法,数据结构"
          />
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="siteForm.difficulty" style="width: 100%">
            <el-option
              v-for="d in TRAINING_DIFFICULTY"
              :key="d"
              :label="d"
              :value="d"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="简介"
          ><el-input v-model="siteForm.description" type="textarea" :rows="2"
        /></el-form-item>
        <el-form-item label="推荐"
          ><el-switch v-model="siteForm.recommended"
        /></el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="siteForm.status"
            active-value="启用"
            inactive-value="停用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="siteVisible = false">取消</el-button>
        <el-button type="primary" :loading="siteSaving" @click="saveSite"
          >保存</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import {
  COMPETITION_STATUS_TYPE,
  competitionStatusLabel,
  isCompetitionStartingSoon
} from '../utils/competitionStatus'
import {
  LEVELS,
  TRAINING_COMPETITIONS,
  TRAINING_DIFFICULTY
} from '../utils/constants'
import {
  draftStatusMeta,
  jobStatusMeta,
  publishBlockers,
  evidenceRows,
  jobProgress
} from '../utils/draft'
import { formatDateTime } from '../utils/advice'

const tab = ref('competition')
const router = useRouter()
const list = ref([])
const loading = ref(false)
const addVisible = ref(false)
const saving = ref(false)
const feedbacks = ref([])
const fbLoading = ref(false)
const fbPage = ref(1)
const fbPageSize = 10
const fbTotal = ref(0)
const logs = ref([])
const logLoading = ref(false)
const logPage = ref(1)
const logPageSize = 10
const logTotal = ref(0)
const achStatus = ref('待审核')
const achList = ref([])
const achLoading = ref(false)
const achPage = ref(1)
const achPageSize = 10
const achTotal = ref(0)
// 训练资源管理
const sites = ref([])
const siteLoading = ref(false)
const sitePage = ref(1)
const sitePageSize = 10
const siteTotal = ref(0)
const siteKeyword = ref('')
const siteVisible = ref(false)
const siteSaving = ref(false)
const siteForm = reactive({
  id: null,
  name: '',
  url: '',
  competition: '通用',
  tags: '',
  difficulty: '入门',
  description: '',
  recommended: false,
  status: '启用'
})
const form = reactive({
  name: '',
  organizer: '',
  level: '校级',
  signupStart: '',
  signupEnd: '',
  startTime: '',
  description: ''
})

// ===== 赛事发现 =====
const discoveryStatus = ref(null)
const discoveryLoading = ref(false)
const discoveryForm = reactive({
  mode: 'SEARCH',
  keyword: '',
  direction: '',
  region: '',
  windowDays: 90,
  maxResults: 10,
  url: '',
  text: '',
  pageTitle: ''
})
const job = ref(null)
let jobTimer = null
const drafts = ref([])
const draftLoading = ref(false)
const draftStatus = ref('DRAFT')
const draftDialog = ref(false)
const draftDetail = ref(null)
const draftSaving = ref(false)
const draftForm = reactive({
  name: '',
  organizer: '',
  year: null,
  edition: '',
  track: '',
  level: '',
  description: '',
  tags: '',
  eligibilityText: '',
  signupStart: '',
  signupEnd: '',
  startTime: '',
  signupEndPrecision: 'DATETIME',
  dateNote: '',
  reviewNote: '',
  duplicateNote: '',
  confirmDateInterpretation: false,
  duplicateAcknowledged: false
})

async function load() {
  loading.value = true
  try {
    list.value = await api.get('/competitions')
  } catch {
    // 接口层提示错误，保留已有列表。
  } finally {
    loading.value = false
  }
}

async function loadFeedback() {
  fbLoading.value = true
  try {
    const data = await api.get('/feedback', {
      params: { page: fbPage.value, size: fbPageSize }
    })
    feedbacks.value = data.records || []
    fbTotal.value = data.total || 0
  } finally {
    fbLoading.value = false
  }
}

function onFbPageChange(p) {
  fbPage.value = p
  loadFeedback()
}

async function loadLogs() {
  logLoading.value = true
  try {
    const data = await api.get('/admin/logs', {
      params: { page: logPage.value, size: logPageSize }
    })
    logs.value = data.records || []
    logTotal.value = data.total || 0
  } finally {
    logLoading.value = false
  }
}

function onLogPageChange(p) {
  logPage.value = p
  loadLogs()
}

async function loadAchievements() {
  achLoading.value = true
  try {
    const data = await api.get('/achievements/admin', {
      params: {
        status: achStatus.value,
        page: achPage.value,
        size: achPageSize
      }
    })
    achList.value = data.records || []
    achTotal.value = data.total || 0
  } finally {
    achLoading.value = false
  }
}

function onAchPageChange(p) {
  achPage.value = p
  loadAchievements()
}

async function reviewAchievement(row, action) {
  await ElMessageBox.confirm(
    `确定${action === 'approve' ? '通过' : '拒绝'}成果「${row.name}」的审核吗？`,
    '成果审核',
    { type: 'warning' }
  )
  await api.post(`/achievements/admin/${row.id}/${action}`)
  ElMessage.success(action === 'approve' ? '已通过，将公开展示' : '已拒绝')
  loadAchievements()
}

async function removeAchievement(row) {
  await ElMessageBox.confirm(`确定删除成果「${row.name}」吗？`, '删除确认', {
    type: 'warning'
  })
  await api.delete(`/achievements/${row.id}`)
  ElMessage.success('已删除')
  loadAchievements()
}

async function doAdd() {
  if (!form.name) {
    ElMessage.warning('请填写竞赛名称')
    return
  }
  saving.value = true
  try {
    await api.post('/competitions', form)
    ElMessage.success('竞赛已添加')
    addVisible.value = false
    Object.keys(form).forEach((k) => (form[k] = ''))
    form.level = '校级'
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除竞赛「${row.name}」吗？`, '删除确认', {
    type: 'warning'
  })
  await api.delete(`/competitions/${row.id}`)
  ElMessage.success('已删除')
  load()
}

async function handleFeedback(row) {
  await ElMessageBox.confirm(
    `确认将「${row.content.slice(0, 20)}...」标记为已处理吗？`,
    '处理反馈',
    { type: 'info' }
  )
  await api.post(`/feedback/${row.id}/handle`)
  ElMessage.success('已标记为处理完成')
  loadFeedback()
}

// ===== 训练资源管理 =====
async function loadSites() {
  siteLoading.value = true
  try {
    const data = await api.get('/training/admin/sites', {
      params: {
        keyword: siteKeyword.value,
        page: sitePage.value,
        size: sitePageSize
      }
    })
    sites.value = data.records || []
    siteTotal.value = data.total || 0
  } finally {
    siteLoading.value = false
  }
}

function onSitePageChange(p) {
  sitePage.value = p
  loadSites()
}

function openSiteAdd() {
  Object.assign(siteForm, {
    id: null,
    name: '',
    url: '',
    competition: '通用',
    tags: '',
    difficulty: '入门',
    description: '',
    recommended: false,
    status: '启用'
  })
  siteVisible.value = true
}

function openSiteEdit(row) {
  Object.assign(siteForm, {
    id: row.id,
    name: row.name,
    url: row.url,
    competition: row.competition,
    tags: row.tags,
    difficulty: row.difficulty,
    description: row.description,
    recommended: !!row.recommended,
    status: row.status
  })
  siteVisible.value = true
}

async function saveSite() {
  if (!siteForm.name || !siteForm.url) {
    ElMessage.warning('请填写资源名和链接')
    return
  }
  siteSaving.value = true
  try {
    if (siteForm.id) {
      await api.put(`/training/admin/sites/${siteForm.id}`, siteForm)
    } else {
      await api.post('/training/admin/sites', siteForm)
    }
    ElMessage.success('已保存')
    siteVisible.value = false
    loadSites()
  } finally {
    siteSaving.value = false
  }
}

async function removeSite(row) {
  await ElMessageBox.confirm(
    `确定删除训练资源「${row.name}」吗？`,
    '删除确认',
    { type: 'warning' }
  )
  await api.delete(`/training/admin/sites/${row.id}`)
  ElMessage.success('已删除')
  loadSites()
}

// ===== 赛事发现 =====
async function loadDiscoveryStatus() {
  try {
    discoveryStatus.value = await api.get('/admin/competition-discovery/status')
  } catch {
    discoveryStatus.value = null
  }
}

async function loadDrafts() {
  draftLoading.value = true
  try {
    const data = await api.get('/admin/competition-drafts', {
      params: { status: draftStatus.value, page: 1, size: 20 }
    })
    drafts.value = data.records || []
  } catch {
    drafts.value = []
  } finally {
    draftLoading.value = false
  }
}

function startPolling(id) {
  if (jobTimer) clearInterval(jobTimer)
  const started = Date.now()
  // 后端作业有总时限，前端多等 30 秒后停止轮询，避免无限等待。
  const limitMs = ((discoveryStatus.value?.jobTimeoutSeconds || 300) + 30) * 1000
  const tick = async () => {
    try {
      const data = await api.get(`/admin/competition-discovery/jobs/${id}`)
      job.value = data
      if (['SUCCEEDED', 'PARTIAL', 'FAILED'].includes(data.status)) {
        clearInterval(jobTimer)
        jobTimer = null
        discoveryLoading.value = false
        await loadDrafts()
        if (data.status === 'SUCCEEDED') {
          ElMessage.success(data.message)
        } else if (data.status === 'PARTIAL') {
          ElMessage.warning(data.message)
        } else {
          ElMessage.error(data.message)
        }
      } else if (Date.now() - started > limitMs) {
        clearInterval(jobTimer)
        jobTimer = null
        discoveryLoading.value = false
        ElMessage.warning('作业仍未返回结果，可稍后刷新查看；如长期无结果请检查服务日志')
      }
    } catch {
      clearInterval(jobTimer)
      jobTimer = null
      discoveryLoading.value = false
    }
  }
  jobTimer = setInterval(tick, 2000)
  tick()
}

async function startDiscovery() {
  if (discoveryLoading.value) return
  const payload = { mode: discoveryForm.mode, windowDays: discoveryForm.windowDays }
  if (discoveryForm.mode === 'SEARCH') {
    if (!discoveryForm.keyword.trim() && !discoveryForm.direction.trim()) {
      ElMessage.warning('请填写关键词或方向')
      return
    }
    Object.assign(payload, {
      keyword: discoveryForm.keyword,
      direction: discoveryForm.direction,
      region: discoveryForm.region,
      maxResults: discoveryForm.maxResults
    })
  } else if (discoveryForm.mode === 'URL') {
    if (!discoveryForm.url.trim()) {
      ElMessage.warning('请填写公告 URL')
      return
    }
    payload.url = discoveryForm.url
  } else {
    if (discoveryForm.text.trim().length < 20) {
      ElMessage.warning('请粘贴至少 20 字的公告正文')
      return
    }
    payload.text = discoveryForm.text
    payload.pageTitle = discoveryForm.pageTitle
  }
  discoveryLoading.value = true
  job.value = { status: 'QUEUED', message: '正在创建作业…' }
  try {
    const id = await api.post('/admin/competition-discovery/jobs', payload)
    job.value = { id, status: 'QUEUED', message: '已排队' }
    startPolling(id)
  } catch (error) {
    discoveryLoading.value = false
    job.value = null
    ElMessage.error(error?.message || '无法启动发现作业')
  }
}

function applyDraftDetail(data) {
  draftDetail.value = data
  Object.assign(draftForm, {
    name: data.name || '',
    organizer: data.organizer || '',
    year: data.year || null,
    edition: data.edition || '',
    track: data.track || '',
    level: data.level || '',
    description: data.description || '',
    tags: data.tags || '',
    eligibilityText: data.eligibilityText || '',
    signupStart: data.signupStart || '',
    signupEnd: data.signupEndPrecision === 'DATE'
      ? String(data.signupEnd || '').slice(0, 10)
      : data.signupEnd || '',
    startTime: data.startTime || '',
    signupEndPrecision: data.signupEndPrecision || 'DATETIME',
    dateNote: data.dateNote || '',
    reviewNote: data.reviewNote || '',
    duplicateNote: data.duplicateNote || '',
    confirmDateInterpretation: !!data.dateInterpretationConfirmed,
    duplicateAcknowledged: !!data.duplicateAcknowledged
  })
  draftDialog.value = true
}

async function openDraft(row) {
  try {
    applyDraftDetail(await api.get(`/admin/competition-drafts/${row.id}`))
  } catch {
    // 接口层已提示
  }
}

function draftPayload(action) {
  return {
    action,
    version: draftDetail.value.version,
    name: draftForm.name,
    organizer: draftForm.organizer,
    year: draftForm.year,
    edition: draftForm.edition,
    track: draftForm.track,
    level: draftForm.level,
    description: draftForm.description,
    tags: draftForm.tags,
    eligibilityText: draftForm.eligibilityText,
    signupStart: draftForm.signupStart,
    signupEnd: draftForm.signupEnd,
    startTime: draftForm.startTime,
    signupEndPrecision: draftForm.signupEndPrecision,
    dateNote: draftForm.dateNote,
    reviewNote: draftForm.reviewNote,
    duplicateNote: draftForm.duplicateNote,
    confirmDateInterpretation: draftForm.confirmDateInterpretation,
    duplicateAcknowledged: draftForm.duplicateAcknowledged
  }
}

async function saveDraft(action = 'SAVE') {
  if (!draftDetail.value) return
  draftSaving.value = true
  try {
    const data = await api.put(
      `/admin/competition-drafts/${draftDetail.value.id}`,
      draftPayload(action)
    )
    applyDraftDetail(data)
    await loadDrafts()
    ElMessage.success(action === 'MARK_READY' ? '已标记为待发布' : '草稿已保存')
  } catch (error) {
    ElMessage.error(error?.message || '保存失败')
  } finally {
    draftSaving.value = false
  }
}

async function rejectDraft() {
  if (!draftDetail.value) return
  const { value } = await ElMessageBox.prompt('请填写拒绝原因', '拒绝草稿', {
    inputType: 'textarea',
    inputValidator: (val) => (val && val.trim() ? true : '必须填写原因')
  })
  draftSaving.value = true
  try {
    const payload = draftPayload('REJECT')
    payload.reviewNote = value
    const data = await api.put(
      `/admin/competition-drafts/${draftDetail.value.id}`,
      payload
    )
    applyDraftDetail(data)
    await loadDrafts()
    ElMessage.success('已拒绝该草稿')
  } catch (error) {
    ElMessage.error(error?.message || '操作失败')
  } finally {
    draftSaving.value = false
  }
}

async function publishDraft() {
  if (!draftDetail.value) return
  await ElMessageBox.confirm(
    `确认发布「${draftDetail.value.name}」？发布后学生端才可见。`,
    '发布确认',
    { type: 'warning' }
  )
  draftSaving.value = true
  try {
    const competitionId = await api.post(
      `/admin/competition-drafts/${draftDetail.value.id}/publish`,
      null,
      { params: { version: draftDetail.value.version } }
    )
    ElMessage.success(`已发布为竞赛 #${competitionId}`)
    await loadDrafts()
    await load()
    applyDraftDetail(await api.get(`/admin/competition-drafts/${draftDetail.value.id}`))
  } catch (error) {
    ElMessage.error(error?.message || '发布失败')
  } finally {
    draftSaving.value = false
  }
}

let competitionTimer = null
onMounted(() => {
  load()
  loadFeedback()
  loadLogs()
  loadAchievements()
  loadSites()
  loadDiscoveryStatus()
  loadDrafts()
  competitionTimer = setInterval(() => {
    if (!loading.value) load()
  }, 30000)
})
onBeforeUnmount(() => {
  clearInterval(competitionTimer)
  if (jobTimer) clearInterval(jobTimer)
})
</script>

<style scoped>
.card-head {
  display: flex;
  justify-content: flex-end;
}
.competition-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.discovery-status {
  margin-bottom: 14px;
}
.inner-card {
  margin-bottom: 16px;
}
.inline-hint {
  color: #8b97aa;
  font-size: 11px;
  margin-left: 6px;
}
.job-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  font-size: 13px;
}
.job-message {
  color: #62718b;
  font-size: 12px;
  margin: 10px 0;
}
.draft-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin: 18px 0 10px;
  flex-wrap: wrap;
}
.draft-head strong {
  font-size: 14px;
  margin-right: 8px;
}
.draft-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.draft-version {
  color: #8b97aa;
  font-size: 11px;
  margin: 0 8px;
}
.draft-alert {
  margin-bottom: 10px;
}
.draft-alert ul {
  margin: 4px 0 0;
  padding-left: 18px;
  line-height: 1.8;
}
.draft-form {
  margin-top: 8px;
}
.evidence-block {
  margin-top: 16px;
}
.evidence-block h4 {
  font-size: 13px;
  margin-bottom: 8px;
}
.duplicate-row {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  margin: 4px 0;
  font-size: 12px;
}
.raw-text {
  white-space: pre-wrap;
  max-height: 320px;
  overflow: auto;
  background: #f8faff;
  border: 1px solid #edf1f8;
  border-radius: 8px;
  padding: 12px;
  font-size: 11px;
  line-height: 1.8;
  color: #42526e;
}
</style>
