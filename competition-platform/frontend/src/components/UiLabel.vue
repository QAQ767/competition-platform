<template>
  <span class="ui-label"
    ><el-icon aria-hidden="true"><component :is="icon" /></el-icon
    ><span><slot /></span
  ></span>
</template>

<script setup>
defineProps({ icon: { type: String, required: true } })
</script>

<style scoped>
.ui-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  vertical-align: middle;
  line-height: 1.5;
}
.ui-label > .el-icon {
  color: #7a8dab;
  flex-shrink: 0;
  font-size: 17px;
}
</style>
