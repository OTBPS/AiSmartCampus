<template>
  <AppShell>
    <div class="page-head">
      <div>
        <h1>{{ $t('admin.accountTitle') }}</h1>
      </div>
      <div class="page-actions">
        <el-button @click="load">{{ $t('common.refresh') }}</el-button>
        <el-button type="primary" @click="openCreate">{{ $t('admin.addAccount') }}</el-button>
      </div>
    </div>

    <section class="table-panel">
      <div class="admin-filter-bar">
        <el-input
          v-model="filters.keyword"
          clearable
          :placeholder="$t('admin.searchAccountPlaceholder')"
          style="max-width: 300px"
          @keyup.enter="load"
        />
        <el-select v-model="filters.status" clearable :placeholder="$t('common.status')" style="width: 160px">
          <el-option :label="$t('admin.accountActive')" value="ACTIVE" />
          <el-option :label="$t('admin.accountInactive')" value="INACTIVE" />
        </el-select>
        <el-button @click="load">{{ $t('common.search') }}</el-button>
        <span class="filter-count">{{ $t('common.itemsCount', { current: accounts.length, total: accounts.length }) }}</span>
      </div>

      <el-table :data="accounts" height="620">
        <el-table-column prop="username" :label="$t('auth.username')" min-width="180">
          <template #default="{ row }">
            <strong class="account-username">{{ row.username }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="displayName" :label="$t('admin.displayName')" min-width="180" />
        <el-table-column prop="role" :label="$t('admin.role')" width="120" />
        <el-table-column :label="$t('common.status')" width="130">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" effect="plain">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('admin.createdAt')" width="190">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column :label="$t('common.action')" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">{{ $t('common.edit') }}</el-button>
            <el-button
              link
              :type="row.status === 'ACTIVE' ? 'warning' : 'success'"
              @click="toggleStatus(row)"
            >
              {{ row.status === 'ACTIVE' ? $t('admin.deactivateAccount') : $t('admin.activateAccount') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="visible" :title="form.id ? $t('admin.editAccount') : $t('admin.createAccount')" width="520px">
      <el-form ref="accountFormRef" label-position="top" :model="form" :rules="rules">
        <el-form-item :label="$t('auth.username')" prop="username">
          <el-input v-model="form.username" :placeholder="$t('auth.usernamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="form.id ? $t('admin.newPassword') : $t('auth.password')" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="form.id ? $t('admin.passwordUnchanged') : $t('auth.passwordPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="$t('common.status')" prop="status">
          <el-select v-model="form.status" style="width: 100%">
            <el-option :label="$t('admin.accountActive')" value="ACTIVE" />
            <el-option :label="$t('admin.accountInactive')" value="INACTIVE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </AppShell>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import AppShell from '../../components/AppShell.vue'
import { accountApi } from '../../api/modules'

const { t } = useI18n()
const accounts = ref([])
const visible = ref(false)
const saving = ref(false)
const accountFormRef = ref()
const usernamePattern = /^[A-Za-z_]{1,15}$/
const passwordPattern = /^[A-Za-z0-9]{6,20}$/
const filters = reactive({
  keyword: '',
  status: ''
})
const form = reactive(emptyForm())
const rules = {
  username: [{ validator: validateUsername, trigger: 'blur' }],
  password: [{ validator: validatePassword, trigger: 'blur' }],
  status: [{ required: true, message: t('admin.statusRequired'), trigger: 'change' }]
}

onMounted(load)

function emptyForm() {
  return {
    id: null,
    username: '',
    password: '',
    status: 'ACTIVE'
  }
}

async function load() {
  accounts.value = await accountApi.list({
    keyword: filters.keyword || undefined,
    status: filters.status || undefined
  })
}

function openCreate() {
  Object.assign(form, emptyForm())
  visible.value = true
  nextTick(() => accountFormRef.value?.clearValidate())
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    username: row.username,
    password: '',
    status: row.status
  })
  visible.value = true
  nextTick(() => accountFormRef.value?.clearValidate())
}

function validateUsername(_rule, value, callback) {
  if (!usernamePattern.test(value || '')) {
    callback(new Error(t('auth.usernameRule')))
    return
  }
  callback()
}

function validatePassword(_rule, value, callback) {
  if (form.id && !value) {
    callback()
    return
  }
  if (!passwordPattern.test(value || '')) {
    callback(new Error(t('auth.passwordRule')))
    return
  }
  callback()
}

async function save() {
  const valid = await accountFormRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      username: form.username,
      status: form.status
    }
    if (!form.id || form.password) {
      payload.password = form.password
    }
    if (form.id) {
      await accountApi.update(form.id, payload)
    } else {
      await accountApi.create(payload)
    }
    ElMessage.success(t('admin.accountSaved'))
    visible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row) {
  try {
    if (row.status === 'ACTIVE') {
      await ElMessageBox.confirm(t('admin.deactivateConfirm', { username: row.username }), t('admin.deactivateAccount'), {
        confirmButtonText: t('admin.deactivateAccount'),
        cancelButtonText: t('common.cancel'),
        type: 'warning'
      })
      await accountApi.deactivate(row.id)
    } else {
      await accountApi.update(row.id, { username: row.username, status: 'ACTIVE' })
    }
    ElMessage.success(t('admin.accountSaved'))
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || String(error))
    }
  }
}

function statusLabel(status) {
  return status === 'ACTIVE' ? t('admin.accountActive') : t('admin.accountInactive')
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString() : '-'
}
</script>
