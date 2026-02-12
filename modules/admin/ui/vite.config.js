import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';
export default defineConfig(function (_a) {
    var mode = _a.mode;
    var env = loadEnv(mode, process.cwd(), '');
    return {
        plugins: [react()],
        resolve: {
            alias: {
                '@': path.resolve(__dirname, './src'),
            },
        },
        server: {
            port: 3000,
            host: true,
            proxy: {
                '/api/topology': {
                    target: env.VITE_TOPOLOGY_URL || 'http://localhost:1337',
                    changeOrigin: true,
                    rewrite: function (path) { return path.replace(/^\/api\/topology/, ''); },
                },
                '/api/waystation': {
                    target: env.VITE_WAYSTATION_URL || 'http://localhost:1338',
                    changeOrigin: true,
                    rewrite: function (path) { return path.replace(/^\/api\/waystation/, ''); },
                },
                '/api/scheduler': {
                    target: env.VITE_SCHEDULER_URL || 'http://localhost:1339',
                    changeOrigin: true,
                    rewrite: function (path) { return path.replace(/^\/api\/scheduler/, ''); },
                },
                '/api/iam': {
                    target: env.VITE_IAM_URL || 'http://localhost:1340',
                    changeOrigin: true,
                    rewrite: function (path) { return path.replace(/^\/api\/iam/, ''); },
                },
                '/api/history': {
                    target: env.VITE_HISTORY_URL || 'http://localhost:1341',
                    changeOrigin: true,
                    rewrite: function (path) { return path.replace(/^\/api\/history/, ''); },
                },
                '/api/dag-registry': {
                    target: env.VITE_DAG_REGISTRY_URL || 'http://localhost:1342',
                    changeOrigin: true,
                    rewrite: function (path) { return path.replace(/^\/api\/dag-registry/, ''); },
                },
                '/api/secure-store': {
                    target: env.VITE_SECURE_STORE_URL || 'http://localhost:1343',
                    changeOrigin: true,
                    rewrite: function (path) { return path.replace(/^\/api\/secure-store/, ''); },
                },
                '/api/billing': {
                    target: env.VITE_BILLING_URL || 'http://localhost:1344',
                    changeOrigin: true,
                    rewrite: function (path) { return path.replace(/^\/api\/billing/, ''); },
                },
                '/api/kms': {
                    target: env.VITE_KMS_URL || 'http://localhost:1345',
                    changeOrigin: true,
                    rewrite: function (path) { return path.replace(/^\/api\/kms/, ''); },
                },
                '/api/blob-store': {
                    target: env.VITE_BLOB_STORE_URL || 'http://localhost:1346',
                    changeOrigin: true,
                    rewrite: function (path) { return path.replace(/^\/api\/blob-store/, ''); },
                },
                '/api/origin': {
                    target: env.VITE_ORIGIN_HTTP_URL || 'http://localhost:1350',
                    changeOrigin: true,
                    rewrite: function (path) { return path.replace(/^\/api\/origin/, ''); },
                },
            },
        },
        build: {
            outDir: 'dist',
            sourcemap: true,
            rollupOptions: {
                output: {
                    manualChunks: {
                        vendor: ['react', 'react-dom'],
                        router: ['@tanstack/react-router'],
                        query: ['@tanstack/react-query'],
                        ui: ['@radix-ui/react-dropdown-menu', '@radix-ui/react-dialog'],
                        charts: ['recharts', 'reactflow'],
                    },
                },
            },
        },
    };
});
