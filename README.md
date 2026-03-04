<p style="text-align: center">
  <strong>🚀 Build visual, crash-proof workflows in your browser — open source, cloud-native, and extensible.</strong>
</p>

<p style="text-align: center">
  <img src="https://img.shields.io/badge/Kotlin-69.9%25-blue" alt="Kotlin 69.9%">
  <img src="https://img.shields.io/badge/Eclipse_Theia-IDE-1a237e?logo=eclipse&logoColor=white" alt="Eclipse Theia">
  <img src="https://img.shields.io/badge/Temporal.io-Durable_Execution-000?logo=temporal&logoColor=white" alt="Temporal.io">
  <img src="https://img.shields.io/badge/React_Flow-Canvas-ff0072?logo=react&logoColor=white" alt="React Flow">
</p>

<p style="text-align: center">
  <img src="docs/gifs/logo-ani.gif" width="25%" alt="Project Continuum Logo">
</p>

<h1 style="text-align: center">Project Continuum</h1>

<p style="text-align: center">
  <strong>Visual workflows that actually run — and survive.</strong><br/>
  Inspired by KNIME. Made for the cloud. Built to never die.
</p>

<p style="text-align: center">
  <a href="#-quick-start"><img src="https://img.shields.io/badge/Quick_Start-🚀-blue?style=for-the-badge" alt="Quick Start"></a>
  <a href="#-how-it-works"><img src="https://img.shields.io/badge/Architecture-🧱-orange?style=for-the-badge" alt="Architecture"></a>
  <a href="#-contribute"><img src="https://img.shields.io/badge/Contribute-💜-purple?style=for-the-badge" alt="Contribute"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache_2.0-green?style=for-the-badge" alt="License"></a>
</p>

<p style="text-align: center">
  <em>No desktop. No install. Just resilient workflows, in your browser.</em>
</p>

---

## 🔥 Why It's Cool

- **Truly cloud-native** — not a desktop app ported to the web. Built from day one for browsers, containers, and distributed infrastructure.
- **Crash-proof by design** — powered by [Temporal](https://temporal.io), workflows survive process crashes, network failures, and restarts without losing a single step.
- **Watch it happen live** — every node execution streams back to your browser in real time via Kafka → MQTT. No refresh. No polling.
- **Data stays fast** — nodes pass Apache Parquet tables, not JSON blobs. Columnar, compressed, query-ready from the start.
- **Extend without breaking** — add new capabilities by deploying new workers, not by touching existing ones. Zero downtime. Zero coupling.

---

## 🎬 See It In Action

<p style="text-align: center">
  <img src="docs/gifs/Sensor Anomaly.gif" width="80%" alt="Sensor Anomaly Detection Workflow">
</p>

<p style="text-align: center"><em>↑ A real workflow: streaming sensor data → anomaly detection → alert — all drag-and-drop.</em></p>

---

## 💡 The Idea

Start with a drop.
One node. Two.
Transform. Branch. Loop.

Each step tiny.
But at the end — it's a river.
A request turned system.
A click turned outcome.

> **Most tools look good. Then break.**
> We want graphs that keep running —
> even if Kafka dies, even if S3 lags, even if your code crashes.

---

## ✨ Features at a Glance

| | Feature | Description |
|---|---------|-------------|
| 🎨 | **Browser-Native Canvas** | Drag-and-drop workflow editor — real IDE feel, zero install |
| 🔁 | **Indestructible Execution** | Workflows survive crashes, restarts, and infrastructure failures |
| ⚡ | **Live Streaming Updates** | Watch your workflow execute step-by-step in real time |
| 📊 | **Columnar Data Passing** | Parquet tables between nodes — fast, query-ready |
| 🧪 | **AI / ML Ready** | Train models with Unsloth, run inference, all inside your flow |
| 🐳 | **Self-Hostable** | Docker Compose up and you're running |

---

## 🧱 How It Works

```
┌─────────────────────────────────────────────────────┐
│                    BROWSER                           │
│   Eclipse Theia + React Flow (drag & drop canvas)   │
└──────────────────────┬──────────────────────────────┘
                       │ WebSocket / REST
                       ▼
┌─────────────────────────────────────────────────────┐
│              BACKEND (Kotlin + Spring Boot)          │
│         Typed, clean, contract-safe API server       │
└──────┬──────────────────────────────┬───────────────┘
       │                              │
       ▼                              ▼
┌──────────────┐          ┌───────────────────────┐
│   Temporal   │          │   Kafka → MQTT (WS)   │
│  Durable     │          │   Live event stream    │
│  Execution   │          │   step-by-step updates │
└──────────────┘          └───────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────┐
│         Storage: AWS S3 / MinIO (local dev)         │
│         Format: Apache Parquet — columnar, fast     │
└─────────────────────────────────────────────────────┘
```

### The Stack

| Layer | Technology | Why |
|-------|-----------|-----|
| **Canvas** | [Eclipse Theia](https://theia-ide.org/) + [React Flow](https://reactflow.dev/) | Full IDE experience in the browser |
| **Engine** | [Temporal](https://temporal.io) | Durable execution, auto-retry, infinite scale |
| **Events** | Kafka → MQTT over WebSockets | Real-time step-by-step workflow updates |
| **Data** | Apache Parquet | Fast, columnar, query-ready inter-node data |
| **Storage** | AWS S3 / MinIO | Open, portable, no vendor lock-in |
| **Backend** | Kotlin + Spring Boot | Type-safe, clean, battle-tested |
| **Resilience** | Temporal | Fails? Retries. Crashes? Recovers. Forever. |
| **Flow Control** | Output `null` on a port = flow stops | Simple guard logic. Real loops coming. |

---

## 🧬 AI Training Workflows

<p style="text-align: center">
  <img src="docs/gifs/Unsloth1.gif" width="80%" alt="Unsloth AI Training Workflow">
</p>

<p style="text-align: center"><em>↑ Fine-tune LLMs with Unsloth — right inside your workflow graph.</em></p>

---

## 🚀 Quick Start

### Option A: IntelliJ IDEA (Recommended)

> 🧠 **Zero-config IDE setup** — This repo ships with shared [IntelliJ IDEA Run Configurations](https://www.jetbrains.com/help/idea/run-debug-configuration.html) in the `.run/` directory. Just open the project and they'll be **auto-detected** in your Run/Debug toolbar — no manual setup needed.
>
> Included configurations: **ApiServer**, **MessageBridge**, **BaseWorker**, **Frontend**

1. Open the project in IntelliJ IDEA
2. Start infrastructure:
   ```bash
   cd docker && docker compose up -d
   ```
3. Select a run configuration from the toolbar and hit ▶️ — start **ApiServer**, **MessageBridge**, and **BaseWorker**
4. Run the **Frontend** configuration (or manually):
   ```bash
   cd continuum-frontend && yarn install && yarn start:workbench
   ```
5. Open [http://localhost:3002](http://localhost:3002) and start building workflows!

### Option B: Command Line

```bash
# Clone the repo
git clone https://github.com/your-org/Continuum.git
cd Continuum

# Spin up infrastructure (Temporal, Kafka, MinIO, Mosquitto)
cd docker
docker compose up -d

# Build & run the backend
./gradlew :continuum-api-server:bootRun --args='--server.port=8080'

# (In another terminal) Build and run Message Bridge (Kafka → MQTT)
./gradlew :continuum-message-bridge:bootRun --args='--server.port=8082'

# (In another terminal) Start the worker
./gradlew :workers:continuum-base-worker:bootRun --args='--server.port=8081'

# Open the Workbench
cd continuum-frontend
yarn install
yarn build
yarn start:workbench

# Open http://localhost:3002 and start building workflows!
```

> 💡 **Tip:** Pass any `spring.*` property via `--args`:
> ```bash
> ./gradlew :continuum-api-server:bootRun --args='--spring.profiles.active=dev --server.port=9090'
> ```

> 💡 Full setup guide coming soon. For now — explore, break things, open issues.

---

## 🧩 Architecture Philosophy: Workers, Not Monoliths

> **Don't bloat. Distribute.**

Most workflow engines pack every capability into a single worker — and when it breaks, everything breaks.

Continuum takes a different path:

- **One worker = one set of capabilities.** A worker that handles REST calls doesn't need to know about chemistry or AI training.
- **Add capabilities by adding workers — not by inflating existing ones.** Need RDKit nodes? Spin up an RDKit worker. Need Unsloth? That's its own worker. The core stays untouched.
- **Zero downtime for everyone else.** Deploy, update, or crash a worker — other workers keep running. No shared fate.
- **Anyone can host a worker.** You can write and run your own worker offering custom nodes — plug it into the shared registry, and the platform discovers it automatically.

```
┌────────────┐  ┌────────────┐  ┌────────────┐  ┌──────────────┐
│ Base Worker │  │  AI Worker │  │ Chem Worker │  │ Your Worker  │
│  REST, CSV  │  │  Unsloth   │  │   RDKit    │  │  Anything!   │
│  Transform  │  │  Inference │  │  Molecules │  │  Your nodes  │
└─────┬──────┘  └─────┬──────┘  └─────┬──────┘  └──────┬───────┘
      │               │               │                 │
      └───────────────┴───────┬───────┴─────────────────┘
                              ▼
                    ┌──────────────────┐
                    │  Shared Registry  │
                    │  (Auto-discovery) │
                    └──────────────────┘
```

This is the vision: **a marketplace of workers** — lightweight, independent, community-driven.

---

## 🗺️ Roadmap

- [x] Drag-and-drop visual workflow editor
- [x] Durable execution with Temporal
- [x] Live streaming updates via Kafka → MQTT
- [x] Parquet-based data passing between nodes
- [x] Base node library (Transform, REST, Branch, etc.)
- [x] Unsloth AI training node
- [x] IntelliJ IDEA shared run configurations — zero-config dev setup
- [ ] 🔁 True `while` / `for` loops with condition builder
- [ ] 🖥️ Electron standalone — run Continuum as a native desktop app, no browser required
- [ ] 🧪 More RDKit chemistry nodes — full RDKit integration for molecular workflows
- [ ] 🔥 PyTorch nodes — training, inference, and model management natively in your flow
- [ ] 🤖 Full AI training node suite (Unsloth ecosystem)
- [ ] 🔌 Plugin store — Slack, Stripe, Databases, AI services
- [ ] 🙋 Human-in-the-loop — interactive workflows with approval gates, manual review steps, and pause/resume
- [ ] 🐛 Visual debugger with timeline replay
- [ ] 👥 Auth, multi-tenancy & RBAC — authentication, role-based access control, and approval workflows baked in
- [ ] 🏗️ Multi-worker ecosystem — bring your own worker with custom nodes, auto-discovered via shared registry, zero downtime for others
- [ ] 📒 Central node repository — a single registry where all workers publish their available nodes, making them discoverable and composable across the platform
- [ ] ☸️ Helm chart — production-ready Kubernetes deployment for horizontal scaling
- [ ] 📦 Zero-config self-host with `docker compose up`

---

## 📺 YouTube Channel — Coming Soon

We're launching a dedicated YouTube channel with deep dives, demos, and architecture walkthroughs. Subscribe to stay in the loop:

- 🎥 **Live workflow builds** — watch real pipelines come together from scratch
- 🧠 **Architecture breakdowns** — how Temporal, Kafka, and the worker ecosystem fit together
- 💬 **Community discussions** — Q&A, roadmap talks, and contributor spotlights

> 🔔 Channel link dropping soon — star the repo so you don't miss it!

---

## 🤝 Contribute

We don't want perfect. We want **working**.

If you see the gap — fill it. Check out the [Issues](../../issues) page, pick something, and send a PR.

**First time?** Look for issues labeled `good first issue`. We're friendly.

---

## 📄 License

[Apache 2.0](LICENSE) — open, safe, patent-protected.

---

<p style="text-align: center">
  <strong>Welcome. Break it. Fix it. Flow with us.</strong>
</p>

