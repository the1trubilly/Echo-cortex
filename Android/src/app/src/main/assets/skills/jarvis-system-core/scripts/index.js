/*
 * Copyright 2026 Android Jarvis contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

(function (root) {
  'use strict';

  const SCHEMA_VERSION = 1;
  const MAX_STEPS = 64;
  const MAX_STRING_LENGTH = 1000;
  const STEP_TYPES = new Set(['route', 'retrieve', 'generate', 'tool_call', 'respond']);
  const KNOWN_CAPABILITIES = new Set([
    'file:read',
    'file:write',
    'network:fetch',
    'code:execute',
    'memory:read',
    'memory:write',
    'channel:send',
    'tool:invoke',
    'schedule:create',
    'system:admin',
  ]);
  const APPROVAL_REQUIRED = new Set([
    'file:write',
    'code:execute',
    'memory:write',
    'channel:send',
    'schedule:create',
    'system:admin',
  ]);
  const SECRET_KEY_PATTERN = /(?:api.?key|authorization|credential|password|secret|token)/i;

  function asObject(value) {
    return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
  }

  function scrubSecrets(value) {
    return String(value)
      .replace(/\bsk-(?:proj-)?[A-Za-z0-9_-]{12,}\b/g, '[redacted OpenAI key]')
      .replace(/\bgh[pousr]_[A-Za-z0-9_]{12,}\b/g, '[redacted GitHub token]')
      .replace(/\bBearer\s+[A-Za-z0-9._~+/=-]{8,}/gi, 'Bearer [redacted]');
  }

  function asString(value, fallback = '') {
    if (value === undefined || value === null) return fallback;
    return scrubSecrets(value).trim().slice(0, MAX_STRING_LENGTH);
  }

  function asStringArray(value, limit = 32) {
    if (!Array.isArray(value)) return [];
    return [...new Set(value.map((item) => asString(item)).filter(Boolean))].slice(0, limit);
  }

  function clamp(value, minimum, maximum, fallback) {
    const number = Number(value);
    return Number.isFinite(number) ? Math.min(maximum, Math.max(minimum, number)) : fallback;
  }

  function redact(value, depth = 0) {
    if (depth > 8) return '[depth limit]';
    if (typeof value === 'string') return asString(value);
    if (typeof value === 'number' || typeof value === 'boolean' || value === null) return value;
    if (Array.isArray(value)) return value.slice(0, 64).map((item) => redact(item, depth + 1));
    if (value && typeof value === 'object') {
      const output = {};
      Object.entries(value).slice(0, 64).forEach(([key, item]) => {
        output[asString(key, 'field')] = SECRET_KEY_PATTERN.test(key)
          ? '[redacted]'
          : redact(item, depth + 1);
      });
      return output;
    }
    return asString(value);
  }

  function makeId(prefix) {
    const random = Math.random().toString(36).slice(2, 10);
    return `${prefix}-${Date.now().toString(36)}-${random}`;
  }

  function moduleStatus() {
    return {
      ok: true,
      action: 'status',
      schema_version: SCHEMA_VERSION,
      architecture: {
        inspiration: 'OpenJarvis typed system specification',
        primitives: ['intelligence', 'engine', 'agents', 'tools_memory', 'learning'],
      },
      modules: [
        {name: 'typed_system_spec', state: 'prototype_ready', surface: 'skill'},
        {name: 'workflow_planner', state: 'prototype_ready', surface: 'skill'},
        {name: 'capability_gate', state: 'prototype_ready', surface: 'skill'},
        {name: 'trace_and_reflection', state: 'prototype_ready', surface: 'skill'},
        {name: 'tool_execution_loop', state: 'runtime_ready', surface: 'openai_and_local'},
        {name: 'markdown_memory_vault', state: 'app_required', surface: 'native_storage'},
        {name: 'semantic_memory_lattice', state: 'research_planned', surface: 'memory_service'},
        {name: 'scheduler_and_proactive_agents', state: 'app_required', surface: 'background_service'},
        {name: 'self_improvement', state: 'review_gated', surface: 'proposal_pipeline'},
      ],
      limitations: [
        'This skill plans and validates data but cannot invoke another tool.',
        'It is stateless and does not persist traces or memory.',
        'The skill can run only when the active model runtime exposes the run_js tool loop.',
      ],
    };
  }

  function classifyComplexity(goal, toolCount, constraintCount) {
    let score = goal.length > 500 ? 2 : goal.length > 160 ? 1 : 0;
    score += toolCount > 3 ? 2 : toolCount > 0 ? 1 : 0;
    score += constraintCount > 2 ? 1 : 0;
    if (/\b(architect|migrate|research|debug|integrate|multi[- ]step|self[- ]improv)/i.test(goal)) {
      score += 2;
    }
    return score >= 5 ? 'high' : score >= 2 ? 'medium' : 'low';
  }

  function approvalReport(requestedCapabilities) {
    return requestedCapabilities.map((capability) => ({
      capability,
      known: KNOWN_CAPABILITIES.has(capability),
      approval_required: APPROVAL_REQUIRED.has(capability) || !KNOWN_CAPABILITIES.has(capability),
      reason: !KNOWN_CAPABILITIES.has(capability)
        ? 'Unknown capabilities default to explicit review.'
        : APPROVAL_REQUIRED.has(capability)
          ? 'This capability can change state or produce an external effect.'
          : 'Read-only or invocation-scoped capability.',
    }));
  }

  function buildWorkflow(hasMemoryContext, availableTools) {
    const nodes = [
      {id: 'route', node_type: 'condition', step_type: 'route', purpose: 'Choose the smallest sufficient path.'},
    ];
    if (hasMemoryContext) {
      nodes.push({
        id: 'retrieve',
        node_type: 'tool',
        step_type: 'retrieve',
        purpose: 'Use supplied memory summaries as context; do not claim a fresh retrieval.',
      });
    }
    if (availableTools.length > 0) {
      nodes.push({
        id: 'tools',
        node_type: 'tool',
        step_type: 'tool_call',
        purpose: 'Invoke only a tool that is actually exposed and necessary.',
        available_tools: availableTools,
      });
    }
    nodes.push(
      {id: 'generate', node_type: 'agent', step_type: 'generate', purpose: 'Synthesize an answer from observed evidence.'},
      {id: 'respond', node_type: 'transform', step_type: 'respond', purpose: 'Return the result and label unverified claims.'},
    );
    const edges = nodes.slice(1).map((node, index) => ({source: nodes[index].id, target: node.id}));
    return {nodes, edges, execution_stages: nodes.map((node) => [node.id])};
  }

  function plan(input) {
    const goal = asString(input.goal);
    if (!goal) throw new Error('plan requires a non-empty goal');
    const availableTools = asStringArray(input.available_tools);
    const requestedCapabilities = asStringArray(input.requested_capabilities);
    const constraints = asStringArray(input.constraints);
    const memoryContext = asStringArray(input.memory_context, 16);
    const capabilityChecks = approvalReport(requestedCapabilities);
    const workflow = buildWorkflow(memoryContext.length > 0, availableTools);

    return {
      ok: true,
      action: 'plan',
      schema_version: SCHEMA_VERSION,
      plan: {
        plan_id: makeId('plan'),
        goal_summary: goal,
        complexity: classifyComplexity(goal, availableTools.length, constraints.length),
        system: {
          intelligence: {
            provider: asString(input.provider, 'unknown'),
            model: asString(input.model, 'unknown'),
          },
          engine: {runtime: asString(input.runtime, 'unknown')},
          agents: {
            strategy: availableTools.length > 0 ? 'tool-aware-operative' : 'simple',
            observation_compression: 'bounded-summary',
            task_decomposition: 'phased',
          },
          tools_memory: {
            available_tools: availableTools,
            supplied_memory_context: memoryContext,
            requested_capabilities: requestedCapabilities,
            capability_checks: capabilityChecks,
          },
          learning: {
            trace_types: [...STEP_TYPES],
            reflection_mode: 'proposal-only',
            durable_learning: false,
          },
        },
        constraints,
        workflow,
        approvals_required: capabilityChecks
          .filter((check) => check.approval_required)
          .map((check) => check.capability),
        execution_state: 'planned_not_executed',
      },
      trace_template: {
        trace_id: makeId('trace'),
        query: goal,
        agent: 'jarvis-system-core',
        model: asString(input.model, 'unknown'),
        engine: asString(input.runtime, 'unknown'),
        steps: [],
        outcome: null,
        feedback: null,
      },
    };
  }

  function observe(input) {
    const trace = redact(asObject(input.trace));
    const stepInput = asObject(input.step);
    const stepType = asString(stepInput.step_type);
    if (!STEP_TYPES.has(stepType)) {
      throw new Error(`step_type must be one of: ${[...STEP_TYPES].join(', ')}`);
    }
    const steps = Array.isArray(trace.steps) ? trace.steps.slice(0, MAX_STEPS - 1) : [];
    const success = stepInput.success === undefined ? null : Boolean(stepInput.success);
    steps.push({
      step_type: stepType,
      observed_at: new Date().toISOString(),
      success,
      input: redact(asObject(stepInput.input)),
      output: redact(asObject(stepInput.output)),
      metadata: redact(asObject(stepInput.metadata)),
    });
    return {
      ok: true,
      action: 'observe',
      trace: {
        trace_id: asString(trace.trace_id, makeId('trace')),
        query: asString(trace.query),
        agent: asString(trace.agent, 'jarvis-system-core'),
        model: asString(trace.model, 'unknown'),
        engine: asString(trace.engine, 'unknown'),
        steps,
        outcome: trace.outcome ?? null,
        feedback: trace.feedback ?? null,
      },
      warning: 'An observation records supplied evidence; it does not execute the step.',
    };
  }

  function reflect(input) {
    const trace = redact(asObject(input.trace));
    const steps = Array.isArray(trace.steps) ? trace.steps.slice(0, MAX_STEPS) : [];
    const requestedOutcome = asString(input.outcome);
    const outcome = ['success', 'partial', 'failure'].includes(requestedOutcome)
      ? requestedOutcome
      : 'partial';
    const feedback = input.feedback === undefined || input.feedback === null
      ? null
      : clamp(input.feedback, 0, 1, null);
    const failedSteps = steps.filter((step) => step && step.success === false);
    const toolSteps = steps.filter((step) => step && step.step_type === 'tool_call');
    const findings = [];
    if (steps.length === 0) findings.push('No observed steps were supplied, so execution cannot be assessed.');
    if (failedSteps.length > 0) findings.push(`${failedSteps.length} observed step(s) reported failure.`);
    if (toolSteps.length === 0) findings.push('No tool result was observed; do not imply external actions occurred.');
    if (!steps.some((step) => step && step.step_type === 'respond')) {
      findings.push('The trace has no final response step.');
    }
    if (outcome === 'success' && failedSteps.length > 0) {
      findings.push('The success outcome conflicts with failed observed steps and should be reviewed.');
    }
    const proposals = [];
    if (failedSteps.length > 0) proposals.push('Review the first failed step and make the next attempt narrower.');
    if (steps.length >= MAX_STEPS) proposals.push('Reduce or summarize the workflow before another run.');
    if (feedback !== null && feedback < 0.5) proposals.push('Ask for targeted feedback before changing behavior.');
    if (proposals.length === 0) proposals.push('Keep this trace as evidence; make no automatic behavior change.');

    return {
      ok: true,
      action: 'reflect',
      reflection: {
        trace_id: asString(trace.trace_id, 'unknown'),
        outcome,
        feedback,
        observed_step_count: steps.length,
        findings,
        proposals,
        application_mode: 'proposal_only_requires_review',
      },
    };
  }

  function yamlString(value) {
    return JSON.stringify(asString(value));
  }

  function exportMarkdown(input) {
    const planObject = redact(asObject(input.plan));
    const trace = redact(asObject(input.trace));
    const reflection = redact(asObject(input.reflection));
    const plan = asObject(planObject.plan_id ? planObject : planObject.plan);
    const steps = Array.isArray(trace.steps) ? trace.steps : [];
    const lines = [
      '---',
      'type: jarvis-workflow-trace',
      `schema_version: ${SCHEMA_VERSION}`,
      `plan_id: ${yamlString(plan.plan_id || '')}`,
      `trace_id: ${yamlString(trace.trace_id || reflection.trace_id || '')}`,
      `outcome: ${yamlString(reflection.outcome || trace.outcome || 'unknown')}`,
      '---',
      '',
      '# Jarvis workflow trace',
      '',
      '## Goal',
      '',
      asString(plan.goal_summary || trace.query, 'Not supplied.'),
      '',
      '## Execution state',
      '',
      asString(plan.execution_state, 'unknown'),
      '',
      '## Observed steps',
      '',
    ];
    if (steps.length === 0) {
      lines.push('- No observed steps.');
    } else {
      steps.forEach((step, index) => {
        lines.push(`- ${index + 1}. **${asString(step.step_type, 'unknown')}** — success: ${String(step.success)}`);
        const summary = asString(asObject(step.output).summary);
        if (summary) lines.push(`  - ${summary}`);
      });
    }
    lines.push('', '## Reflection', '');
    const findings = asStringArray(reflection.findings);
    const proposals = asStringArray(reflection.proposals);
    if (findings.length === 0) lines.push('- No findings supplied.');
    findings.forEach((finding) => lines.push(`- ${finding}`));
    lines.push('', '## Proposed improvements', '');
    if (proposals.length === 0) lines.push('- None supplied.');
    proposals.forEach((proposal) => lines.push(`- ${proposal}`));
    lines.push('', '> Plans and proposals are not evidence that an action ran or a change was applied.', '');
    return {ok: true, action: 'export', markdown: lines.join('\n')};
  }

  function execute(rawData) {
    const input = typeof rawData === 'string' ? JSON.parse(rawData) : asObject(rawData);
    switch (asString(input.action).toLowerCase()) {
      case 'status': return moduleStatus();
      case 'plan': return plan(input);
      case 'observe': return observe(input);
      case 'reflect': return reflect(input);
      case 'export': return exportMarkdown(input);
      default: throw new Error('action must be one of: status, plan, observe, reflect, export');
    }
  }

  async function getResult(data) {
    try {
      return JSON.stringify(execute(data));
    } catch (error) {
      return JSON.stringify({
        ok: false,
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }

  root.ai_edge_gallery_get_result = getResult;
  if (typeof module !== 'undefined' && module.exports) {
    module.exports = {execute, getResult};
  }
})(typeof window !== 'undefined' ? window : globalThis);
