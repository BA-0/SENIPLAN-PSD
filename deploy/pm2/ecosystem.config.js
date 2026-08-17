// PM2 process file for the Next.js frontend.
// Deploy: copy this file (and the built frontend/) to /opt/senico-diagnostic/frontend
// then run: pm2 start ecosystem.config.js --env production && pm2 save

module.exports = {
  apps: [
    {
      name: "senico-diagnostic-frontend",
      cwd: "/opt/senico-diagnostic/frontend",
      script: "node_modules/next/dist/bin/next",
      args: "start -p 3000",
      instances: 1,
      exec_mode: "fork",
      autorestart: true,
      max_memory_restart: "400M",
      env_production: {
        NODE_ENV: "production",
        NEXT_PUBLIC_API_BASE_URL: "https://diagnostic.senico.sn/api/v1",
        NEXT_PUBLIC_WS_BASE_URL: "https://diagnostic.senico.sn/ws",
      },
      out_file: "/var/log/senico-diagnostic/frontend-out.log",
      error_file: "/var/log/senico-diagnostic/frontend-error.log",
      time: true,
    },
  ],
};
