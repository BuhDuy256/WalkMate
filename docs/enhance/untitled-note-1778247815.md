---
type: Note
---
#

What was built

File structure (14 files across 6 folders)

system_design_assignment_2/\
├── docker-compose.yml ← 6 services, health checks, dependency chain\
├── .env.example\
├── .gitignore\
│\
├── nginx/\
│ └── nginx.conf ← Round-robin upstream, proxy_next_upstream failover\
│\
├── mysql/\
│ ├── master/my.cnf ← server-id=1, log_bin, binlog_format=ROW\
│ ├── slave/my.cnf ← server-id=2, read_only=ON, super_read_only=ON\
│ └── init/\
│ ├── init-master.sql ← Schema + replicator user + seed data\
│ ├── init-slave.sql ← Mirror schema for slave\
│ └── setup-replication.sh← Auto-runs CHANGE MASTER TO + START SLAVE\
│\
├── api/\
│ ├── Dockerfile ← node:18-alpine\
│ ├── package.json\
│ └── src/\
│ ├── index.js ← Express app + /health endpoint\
│ ├── db.js ← Two pools: masterPool + slavePool\
│ └── routes/products.js ← POST→master, GET→slave, processed_by in response\
│\
├── DOCUMENTATION.md ← Full PDF-ready docs with diagrams, all 4 phases\
└── VIDEO_DEMO_SCRIPT.md ← Timestamped 7-8 min script with exact commands

How to run it

cd system_design_assignment_2\
docker compose up --build -d

# Wait ~60s, then:

curl <http://localhost/health> # alternates node-1 / node-2\
curl -X POST <http://localhost/products>\
\
-H "Content-Type: application/json"\
\
-d '{"name":"Keyboard","price":149.99}'\
curl <http://localhost/products>

# Chaos test:

docker stop api-node-1\
curl <http://localhost/products> # still works, served by node-2\
docker start api-node-1

Grading criteria coverage

┌───────────────────────────┬─────────────────────────────────────────────────────────────────────────────────────┐\
│ Criterion │ How it's met │\
├───────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────┤\
│ System Functionality │ All 6 containers wired with health checks and dependency ordering │\
│ (40%) │ │\
├───────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────┤\
│ Read/Write Splitting │ Two separate MySQL pools in db.js; slave is read_only=ON at DB level │\
│ (20%) │ │\
├───────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────┤\
│ Fault Tolerance (10%) │ proxy_next_upstream in Nginx; chaos test script in docs │\
├───────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────┤\
│ Documentation (15%) │ DOCUMENTATION.md covers all 4 phases with diagrams, config snippets, │\
│ │ troubleshooting │\
├───────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────┤\
│ Video (15%) │ VIDEO_DEMO_SCRIPT.md with exact timestamps and commands │\
└───────────────────────────┴─────────────────────────────────────────────────────────────────────────────────────┘
