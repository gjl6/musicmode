<template>
  <div>
    <n-form-item v-for="field in visibleFields" :key="field.key" :label="field.label">
      <!-- select -->
      <n-select
        v-if="field.type === 'select'"
        :value="getValue(field.key, field.default)"
        :options="getOptions(field)"
        :placeholder="field.placeholder"
        @update:value="v => setValue(field.key, v)"
      />
      <!-- multiselect -->
      <n-select
        v-else-if="field.type === 'multiselect'"
        :value="getValue(field.key, field.default)"
        :options="getOptions(field)"
        multiple
        :placeholder="field.placeholder"
        @update:value="v => setValue(field.key, v)"
      />
      <!-- number -->
      <n-input-number
        v-else-if="field.type === 'number'"
        :value="getValue(field.key, field.default)"
        :placeholder="field.placeholder"
        @update:value="v => setValue(field.key, v)"
      />
      <!-- boolean -->
      <n-switch
        v-else-if="field.type === 'boolean'"
        :value="getValue(field.key, field.default)"
        @update:value="v => setValue(field.key, v)"
      />
      <!-- array / string -->
      <n-input
        v-else
        :value="String(getValue(field.key, field.default) ?? '')"
        :placeholder="field.placeholder"
        @update:value="v => setValue(field.key, v)"
      />
    </n-form-item>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { NFormItem, NInput, NInputNumber, NSelect, NSwitch } from 'naive-ui'

const props = defineProps({
  schema: { type: Array, required: true },
  modelValue: { type: Object, default: () => ({}) },
})

const emit = defineEmits(['update:modelValue'])

function getValue(key, def) {
  const v = props.modelValue[key]
  return v !== undefined ? v : (def !== undefined ? def : null)
}

function setValue(key, val) {
  emit('update:modelValue', { ...props.modelValue, [key]: val })
}

function getOptions(field) {
  if (!field.options) return []
  return field.options.map(opt => ({ value: opt.value, label: opt.label }))
}

const visibleFields = computed(() => {
  return props.schema.filter(field => {
    if (!field.visibleWhen) return true
    const cond = field.visibleWhen
    for (const [k, v] of Object.entries(cond)) {
      const currentVal = getValue(k)
      if (Array.isArray(v)) {
        if (!v.includes(currentVal)) return false
      } else {
        if (currentVal !== v) return false
      }
    }
    return true
  })
})
</script>
