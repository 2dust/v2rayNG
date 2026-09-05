# AGENTS.md

本仓库的工程规则、架构约定、构建命令、编码规范全部集中在 [CLAUDE.md](./CLAUDE.md)。

所有 AI 助手（Claude Code / Codex / 其他 Agent）在改动任何一行代码之前，必须：

1. 完整读完 `CLAUDE.md`；
2. 按 `CLAUDE.md` 中"什么时候必须读哪份规范"的对照表，读完本次任务命中的
   `docs/project-rules/` 下的规范文件；
3. 遵守 `CLAUDE.md` 的「核心规则」与「AI 探索项目的方式」。

规范与源码冲突时，**以源码为准**，并在同一次改动里回头修正规范文件。
