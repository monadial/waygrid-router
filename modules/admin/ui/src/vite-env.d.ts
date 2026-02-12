/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_APP_NAME: string
  readonly VITE_API_TIMEOUT: string
  readonly VITE_TOPOLOGY_URL: string
  readonly VITE_WAYSTATION_URL: string
  readonly VITE_SCHEDULER_URL: string
  readonly VITE_IAM_URL: string
  readonly VITE_HISTORY_URL: string
  readonly VITE_DAG_REGISTRY_URL: string
  readonly VITE_SECURE_STORE_URL: string
  readonly VITE_BILLING_URL: string
  readonly VITE_KMS_URL: string
  readonly VITE_BLOB_STORE_URL: string
  readonly VITE_ORIGIN_HTTP_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
