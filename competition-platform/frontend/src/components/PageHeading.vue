<template>
  <div class="page-heading">
    <div>
      <span v-if="eyebrow" class="eyebrow">{{ eyebrow }}</span>
      <h1>{{ title }}</h1>
      <p>{{ description }}</p>
    </div>
    <slot />
  </div>
</template>

<script setup>
defineProps({ title: String, description: String, eyebrow: String })
</script>
