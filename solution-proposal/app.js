(() => {
  "use strict";

  const slideTitles = [
    "Reliable Notification Delivery",
    "The Challenge",
    "Architecture at a Glance",
    "A Notification Journey",
    "Reliability Under Failure",
    "Self-Service and Tenant Isolation",
    "From Complaint to Root Cause",
    "Security Assessment",
    "Decisions and Engineering Confidence",
    "Case Coverage",
  ];

  const slides = Array.from(document.querySelectorAll("[data-slide]"));
  const explorer = document.querySelector("[data-explorer]");
  const currentNumber = document.querySelector("[data-current-number]");
  const progressTrack = document.querySelector("[data-progress-track]");
  const previousLabel = document.querySelector("[data-previous-label]");
  const nextLabel = document.querySelector("[data-next-label]");
  const previousButtons = Array.from(document.querySelectorAll("[data-previous]"));
  const nextButtons = Array.from(document.querySelectorAll("[data-next]"));
  const explorerLinks = Array.from(explorer.querySelectorAll("[data-go-to]"));
  let activeSlide = 0;

  const icon = (name) => `
    <svg aria-hidden="true">
      <use href="#icon-${name}"></use>
    </svg>
  `;

  const clampSlide = (index) => Math.min(Math.max(index, 0), slides.length - 1);

  const updateNavigation = () => {
    currentNumber.textContent = String(activeSlide + 1).padStart(2, "0");
    progressTrack.style.width = `${((activeSlide + 1) / slides.length) * 100}%`;

    const hasPrevious = activeSlide > 0;
    const hasNext = activeSlide < slides.length - 1;
    previousButtons.forEach((button) => {
      button.disabled = !hasPrevious;
    });
    nextButtons.forEach((button) => {
      button.disabled = !hasNext;
    });

    previousLabel.textContent = hasPrevious ? slideTitles[activeSlide - 1] : "Start";
    nextLabel.textContent = hasNext ? slideTitles[activeSlide + 1] : "Complete";

    explorerLinks.forEach((link) => {
      const isCurrent = Number(link.dataset.goTo) === activeSlide;
      link.classList.toggle("is-current", isCurrent);
      if (isCurrent) {
        link.setAttribute("aria-current", "page");
      } else {
        link.removeAttribute("aria-current");
      }
    });
  };

  const goToSlide = (index, updateHash = true) => {
    const nextIndex = clampSlide(Number(index));
    slides.forEach((slide, slideIndex) => {
      const isActive = slideIndex === nextIndex;
      slide.hidden = !isActive;
      slide.classList.toggle("is-active", isActive);
      slide.setAttribute("aria-hidden", String(!isActive));
      if (isActive) {
        slide.scrollTop = 0;
      }
    });
    activeSlide = nextIndex;
    updateNavigation();
    document.title = `${String(activeSlide + 1).padStart(2, "0")} · ${slideTitles[activeSlide]} — Cobre Notification Platform`;

    if (updateHash) {
      history.replaceState(null, "", `#${slides[activeSlide].id}`);
    }
  };

  document.querySelectorAll("[data-go-to]").forEach((button) => {
    button.addEventListener("click", () => {
      goToSlide(button.dataset.goTo);
      if (explorer.open) {
        explorer.close();
      }
    });
  });

  previousButtons.forEach((button) => button.addEventListener("click", () => goToSlide(activeSlide - 1)));
  nextButtons.forEach((button) => button.addEventListener("click", () => goToSlide(activeSlide + 1)));

  document.querySelectorAll("[data-open-explorer]").forEach((button) => {
    button.addEventListener("click", () => explorer.showModal());
  });
  document.querySelector("[data-close-explorer]").addEventListener("click", () => explorer.close());
  explorer.addEventListener("click", (event) => {
    if (event.target === explorer) {
      explorer.close();
    }
  });

  window.addEventListener("keydown", (event) => {
    if (explorer.open || event.defaultPrevented || event.metaKey || event.ctrlKey || event.altKey) {
      return;
    }
    const target = event.target;
    const isInteractive = target instanceof Element && target.closest("button, a, summary, input, select, textarea");
    if (isInteractive) {
      return;
    }
    if (event.key === "ArrowRight" || event.key === "PageDown") {
      event.preventDefault();
      goToSlide(activeSlide + 1);
    }
    if (event.key === "ArrowLeft" || event.key === "PageUp") {
      event.preventDefault();
      goToSlide(activeSlide - 1);
    }
    if (event.key === "Home") {
      event.preventDefault();
      goToSlide(0);
    }
    if (event.key === "End") {
      event.preventDefault();
      goToSlide(slides.length - 1);
    }
  });

  window.addEventListener("hashchange", () => {
    const targetId = location.hash.slice(1);
    const targetIndex = slides.findIndex((slide) => slide.id === targetId);
    if (targetIndex >= 0 && targetIndex !== activeSlide) {
      goToSlide(targetIndex, false);
    }
  });

  // Cover overview
  const coverDetailOutput = document.querySelector("[data-cover-detail-output]");
  document.querySelectorAll("[data-cover-detail]").forEach((button) => {
    button.addEventListener("click", () => {
      coverDetailOutput.textContent = button.dataset.coverDetail;
    });
  });

  // Challenge
  const challengeData = {
    delivery: {
      index: "01",
      title: "Reliable Delivery",
      description:
        "Confirm ownership, resolve the active subscription, send the HTTPS webhook and make every outcome durable.",
    },
    "self-service": {
      index: "02",
      title: "Client Self-Service",
      description:
        "Give each authenticated client a bounded view of its events and accept replay only for a definitively failed delivery.",
    },
    operations: {
      index: "03",
      title: "Operational Response",
      description:
        "Expose near real-time signals and persisted evidence so monitoring can move from a client complaint to a specific cause.",
    },
  };
  const challengeIndex = document.querySelector("[data-challenge-index]");
  const challengeTitle = document.querySelector("[data-challenge-title]");
  const challengeDescription = document.querySelector("[data-challenge-description]");

  document.querySelectorAll("[data-challenge]").forEach((button) => {
    button.addEventListener("click", () => {
      const selected = challengeData[button.dataset.challenge];
      document.querySelectorAll("[data-challenge]").forEach((option) => option.classList.toggle("is-selected", option === button));
      challengeIndex.textContent = selected.index;
      challengeTitle.textContent = selected.title;
      challengeDescription.textContent = selected.description;
    });
  });

  // Architecture
  const architectureCanvas = document.querySelector("[data-architecture-canvas]");
  const architectureCaption = document.querySelector("[data-architecture-caption]");

  const renderInsideArchitecture = () => {
    architectureCanvas.innerHTML = `
      <div class="arch-flow">
        <div class="arch-stack">
          <div class="arch-node">${icon("api")}<span>REST API</span></div>
          <div class="arch-node">${icon("file")}<span>JSON importer</span></div>
          <div class="arch-node">${icon("clock")}<span>Scheduled worker</span></div>
        </div>
        <i class="arch-connector" aria-hidden="true"></i>
        <div class="arch-port"><span>Inbound<br />ports</span></div>
        <i class="arch-connector" aria-hidden="true"></i>
        <div class="arch-core">
          <div class="arch-core-node">${icon("platform")}<strong>Application services</strong></div>
          <i class="arch-core-divider" aria-hidden="true"></i>
          <div class="arch-core-node">${icon("shield")}<strong>Domain rules</strong></div>
        </div>
        <i class="arch-connector" aria-hidden="true"></i>
        <div class="arch-port"><span>Outbound<br />ports</span></div>
        <i class="arch-connector" aria-hidden="true"></i>
        <div class="arch-stack">
          <div class="arch-node">${icon("database")}<span>PostgreSQL JDBC</span></div>
          <div class="arch-node">${icon("globe")}<span>HTTPS webhook</span></div>
          <div class="arch-node">${icon("chart")}<span>Prometheus metrics</span></div>
        </div>
      </div>
    `;
    architectureCaption.textContent =
      "Inbound adapters invoke application ports; the core owns orchestration and domain rules; outbound ports isolate infrastructure.";
  };

  const renderSystemContext = () => {
    architectureCanvas.innerHTML = `
      <div class="arch-context">
        <div class="arch-context-side">
          <div class="arch-context-node">${icon("code")}<span>Cobre event source</span></div>
          <div class="arch-context-node">${icon("user")}<span>Client user</span></div>
        </div>
        <i class="arch-connector" aria-hidden="true"></i>
        <div class="arch-context-node arch-context-platform">${icon("platform")}<strong>Notification Platform</strong></div>
        <i class="arch-connector" aria-hidden="true"></i>
        <div class="arch-context-side">
          <div class="arch-context-node">${icon("globe")}<span>Client backend</span></div>
          <div class="arch-context-node">${icon("chart")}<span>Monitoring team</span></div>
        </div>
      </div>
    `;
    architectureCaption.textContent =
      "The platform owns notification delivery and its operational history; the client owns the side effect after receiving the webhook.";
  };

  document.querySelectorAll("[data-architecture-view]").forEach((button) => {
    button.addEventListener("click", () => {
      document.querySelectorAll("[data-architecture-view]").forEach((option) => {
        const selected = option === button;
        option.classList.toggle("is-selected", selected);
        option.setAttribute("aria-selected", String(selected));
      });
      if (button.dataset.architectureView === "context") {
        renderSystemContext();
      } else {
        renderInsideArchitecture();
      }
    });
  });
  renderSystemContext();

  // Notification journey
  const journeyData = [
    ["JSON importer", "Validated source event", "Imported status preserved"],
    ["Import repository", "Event row and source timestamps", "PENDING or source final state"],
    ["Preparation service", "Subscription and destination snapshot", "PROCESSING"],
    ["Claim repository", "Worker id and time-bounded lease", "PROCESSING"],
    ["Attempt repository", "Delivery cycle, attempt number and correlation id", "PROCESSING"],
    ["HTTPS adapter", "HTTP status, latency or bounded failure", "Outcome classified"],
    ["Completion service", "Closed attempt and next event state", "COMPLETED, RETRY_SCHEDULED or FAILED"],
  ];
  const journeyOwner = document.querySelector("[data-journey-owner]");
  const journeyEvidence = document.querySelector("[data-journey-evidence]");
  const journeyState = document.querySelector("[data-journey-state]");

  document.querySelectorAll("[data-journey-step]").forEach((button) => {
    button.addEventListener("click", () => {
      const [owner, evidence, state] = journeyData[Number(button.dataset.journeyStep)];
      document.querySelectorAll("[data-journey-step]").forEach((step) => step.classList.toggle("is-selected", step === button));
      journeyOwner.textContent = owner;
      journeyEvidence.textContent = evidence;
      journeyState.textContent = state;
    });
  });

  // Reliability scenarios
  const reliabilityScenarios = {
    success: {
      nodes: [
        ["globe", "HTTP 2xx"],
        ["check", "SUCCESS"],
        ["database", "Attempt persisted", true],
        ["check", "COMPLETED"],
        ["check", "No retry"],
      ],
      result: "Success",
      state: "COMPLETED",
      evidence: "Attempt record with HTTP status and latency",
      action: "No further delivery action",
      note: "The client accepted the webhook and the final event state is durable.",
      tone: "positive",
      noteIcon: "check",
    },
    "rate-limit": {
      nodes: [
        ["globe", "HTTP 429"],
        ["retry", "RETRYABLE_FAILURE"],
        ["database", "Attempt persisted", true],
        ["clock", "RETRY_SCHEDULED"],
        ["clock", "Configured backoff"],
      ],
      result: "Retryable HTTP response",
      state: "RETRY_SCHEDULED",
      evidence: "Attempt record with HTTP 429 and latency",
      action: "Retry after configured backoff",
      note: "Rate limiting is treated as a temporary condition and consumes an attempt.",
      tone: "warning",
      noteIcon: "alert",
    },
    "server-error": {
      nodes: [
        ["globe", "HTTP 500"],
        ["retry", "RETRYABLE_FAILURE"],
        ["database", "Attempt persisted", true],
        ["clock", "RETRY_SCHEDULED"],
        ["clock", "Configured backoff"],
      ],
      result: "Retryable HTTP response",
      state: "RETRY_SCHEDULED",
      evidence: "Attempt record with 5xx status and latency",
      action: "Retry after configured backoff",
      note: "Server errors are retried until delivery succeeds or the configured attempt limit is exhausted.",
      tone: "warning",
      noteIcon: "alert",
    },
    timeout: {
      nodes: [
        ["clock", "Webhook timeout"],
        ["retry", "RETRYABLE_FAILURE"],
        ["database", "Attempt persisted", true],
        ["clock", "RETRY_SCHEDULED"],
        ["clock", "Configured backoff"],
      ],
      result: "Timeout",
      state: "RETRY_SCHEDULED",
      evidence: "Attempt record with timestamps and failure category",
      action: "Retry after configured backoff",
      note: "The client may have processed the request. Delivery remains at-least-once.",
      tone: "warning",
      noteIcon: "alert",
    },
    tls: {
      nodes: [
        ["shield", "TLS validation failure"],
        ["alert", "PERMANENT_FAILURE"],
        ["database", "Attempt persisted", true],
        ["alert", "FAILED"],
        ["close", "No automatic retry"],
      ],
      result: "Permanent transport failure",
      state: "FAILED",
      evidence: "Attempt record with TLS_ERROR category",
      action: "Wait for an explicit replay",
      note: "TLS validation failures are not retried automatically because configuration or trust must be corrected.",
      tone: "negative",
      noteIcon: "alert",
    },
    crash: {
      nodes: [
        ["clock", "Lease expires"],
        ["retry", "WORKER_LEASE_EXPIRED"],
        ["database", "Attempt recovered", true],
        ["clock", "RETRY_SCHEDULED"],
        ["clock", "Configured backoff"],
      ],
      result: "Abandoned worker ownership",
      state: "RETRY_SCHEDULED or FAILED",
      evidence: "Closed attempt and lease-recovery origin",
      action: "Apply the current retry policy",
      note: "Recovery preserves lease ownership and prevents a late worker result from overwriting the recovered state.",
      tone: "warning",
      noteIcon: "alert",
    },
  };

  const reliabilityFlow = document.querySelector("[data-reliability-flow]");
  const reliabilityNote = document.querySelector("[data-reliability-note]");
  const outcomeResult = document.querySelector("[data-outcome-result]");
  const outcomeState = document.querySelector("[data-outcome-state]");
  const outcomeEvidence = document.querySelector("[data-outcome-evidence]");
  const outcomeAction = document.querySelector("[data-outcome-action]");

  const renderReliabilityScenario = (scenarioName) => {
    const scenario = reliabilityScenarios[scenarioName];
    reliabilityFlow.innerHTML = scenario.nodes
      .map(([nodeIcon, label, persisted], index) => {
        const node = `<div class="flow-node${persisted ? " is-persisted" : ""}">${icon(nodeIcon)}<strong>${label}</strong></div>`;
        return index < scenario.nodes.length - 1 ? `${node}<i class="flow-arrow" aria-hidden="true"></i>` : node;
      })
      .join("");
    reliabilityNote.classList.toggle("is-positive", scenario.tone === "positive");
    reliabilityNote.classList.toggle("is-negative", scenario.tone === "negative");
    reliabilityNote.innerHTML = `${icon(scenario.noteIcon)}<p>${scenario.note}</p>`;
    outcomeResult.textContent = scenario.result;
    outcomeState.textContent = scenario.state;
    outcomeEvidence.textContent = scenario.evidence;
    outcomeAction.textContent = scenario.action;
  };

  document.querySelectorAll("[data-scenario]").forEach((button) => {
    button.addEventListener("click", () => {
      document.querySelectorAll("[data-scenario]").forEach((option) => {
        const selected = option === button;
        option.classList.toggle("is-selected", selected);
        option.setAttribute("aria-selected", String(selected));
      });
      renderReliabilityScenario(button.dataset.scenario);
    });
  });
  renderReliabilityScenario("success");

  // Self-service API
  const endpointData = {
    list: "Returns only the authenticated client's events with date, status and bounded pagination filters.",
    details: "Returns one tenant-scoped event without exposing worker ownership, destination or internal delivery fields.",
    replay: "Accepts only a FAILED event, creates a new delivery cycle and responds with 202 Accepted.",
  };
  const endpointDetail = document.querySelector("[data-endpoint-detail]");
  document.querySelectorAll("[data-endpoint]").forEach((button) => {
    button.addEventListener("click", () => {
      document.querySelectorAll("[data-endpoint]").forEach((endpoint) => {
        const selected = endpoint === button;
        endpoint.classList.toggle("is-selected", selected);
        endpoint.setAttribute("aria-pressed", String(selected));
      });
      endpointDetail.textContent = endpointData[button.dataset.endpoint];
    });
  });

  const accessEvent = document.querySelector("[data-access-event]");
  const accessResult = document.querySelector("[data-access-result]");
  const accessExplanation = document.querySelector("[data-access-explanation]");
  document.querySelectorAll("[data-access]").forEach((button) => {
    button.addEventListener("click", () => {
      const isOwnEvent = button.dataset.access === "own";
      document.querySelectorAll("[data-access]").forEach((option) => {
        const selected = option === button;
        option.classList.toggle("is-selected", selected);
        option.setAttribute("aria-selected", String(selected));
      });
      accessEvent.textContent = isOwnEvent ? "EVT003 · CLIENT002" : "EVT001 · CLIENT001";
      accessResult.textContent = isOwnEvent ? "200 OK" : "404 NOT FOUND";
      accessExplanation.textContent = isOwnEvent
        ? "The event belongs to the authenticated client."
        : "404 intentionally conceals whether another tenant's event exists.";
      accessResult.classList.toggle("is-success", isOwnEvent);
      accessResult.classList.toggle("is-not-found", !isOwnEvent);
    });
  });

  // Operations
  const diagnosticDetails = [
    "Prometheus evaluates a delivery deviation and Alertmanager exposes the active signal.",
    "Client labels identify CLIENT002 without using an event identifier as a metric label.",
    "The affected credit_transfer stream narrows the operational surface.",
    "The operator uses client_id and event_id to request one tenant-scoped investigation.",
    "The ordered timeline shows each result, status class, latency and correlation identifier.",
    "Bounded failure categories turn a raw attempt history into an actionable operational cause.",
  ];
  const diagnosticIcons = ["bell", "user", "tag", "file", "clock", "alert"];
  const diagnosticDetail = document.querySelector("[data-diagnostic-detail]");
  const diagnosticDetailIcon = document.querySelector("[data-diagnostic-detail-icon]");
  document.querySelectorAll("[data-diagnostic-step]").forEach((button) => {
    button.addEventListener("click", () => {
      const index = Number(button.dataset.diagnosticStep);
      document.querySelectorAll("[data-diagnostic-step]").forEach((step) => {
        const selected = step === button;
        step.classList.toggle("is-selected", selected);
        step.setAttribute("aria-pressed", String(selected));
      });
      diagnosticDetail.textContent = diagnosticDetails[index];
      diagnosticDetailIcon.setAttribute("href", `#icon-${diagnosticIcons[index]}`);
      diagnosticDetail.parentElement.classList.remove("is-updated");
      void diagnosticDetail.parentElement.offsetWidth;
      diagnosticDetail.parentElement.classList.add("is-updated");
    });
  });

  // Security
  const risks = {
    access: {
      threat: "A client attempts to read or replay another client's notification.",
      control: "Principal-derived client ownership and tenant predicates on every read and mutation.",
      residual: "Low",
      code: "WHERE event_id = :notificationEventId\n  AND client_id = :clientId",
    },
    injection: {
      threat: "A crafted request value attempts to alter a persistence query.",
      control: "Prepared statements, named parameters and fixed application-owned SQL fragments.",
      residual: "Very low",
      code: "parameters.addValue(\"deliveryStatus\",\n    query.deliveryStatus().name());",
    },
    supply: {
      threat: "A vulnerable transitive dependency compromises the runtime without changing application code.",
      control: "OWASP Dependency-Check scans runtime dependencies and blocks high-severity findings in CI.",
      residual: "Low",
      code: "failBuildOnCVSS = 7.0\nscanConfigurations = ['runtimeClasspath']",
    },
  };
  const riskThreat = document.querySelector("[data-risk-threat]");
  const riskControl = document.querySelector("[data-risk-control]");
  const riskResidual = document.querySelector("[data-risk-residual]");
  const riskCode = document.querySelector("[data-risk-code]");
  document.querySelectorAll("[data-risk]").forEach((button) => {
    button.addEventListener("click", () => {
      const risk = risks[button.dataset.risk];
      document.querySelectorAll("[data-risk]").forEach((row) => {
        const selected = row === button;
        row.classList.toggle("is-selected", selected);
        row.setAttribute("aria-pressed", String(selected));
      });
      riskThreat.textContent = risk.threat;
      riskControl.textContent = risk.control;
      riskResidual.textContent = risk.residual;
      riskCode.textContent = risk.code;
    });
  });

  // Decisions and quality
  const decisions = {
    postgres: {
      rationale: "Durable state and coordinated claims without another runtime dependency.",
      tradeoff: "Polling and claims consume database capacity.",
    },
    delivery: {
      rationale: "Reflect the ambiguity of failures across an external HTTP boundary.",
      tradeoff: "A timeout can produce a duplicate even with durable local state.",
    },
    deployable: {
      rationale: "Keep the technical case cohesive and operationally simple.",
      tradeoff: "API and worker share scaling and release boundaries.",
    },
    hexagonal: {
      rationale: "Keep delivery rules independent from REST, JDBC and the HTTP client.",
      tradeoff: "Package-level boundaries depend partly on engineering discipline.",
    },
  };
  const decisionRationale = document.querySelector("[data-decision-rationale]");
  const decisionTradeoff = document.querySelector("[data-decision-tradeoff]");
  document.querySelectorAll("[data-decision]").forEach((button) => {
    button.addEventListener("click", () => {
      const decision = decisions[button.dataset.decision];
      document.querySelectorAll("[data-decision]").forEach((row) => {
        const selected = row === button;
        row.classList.toggle("is-selected", selected);
        row.setAttribute("aria-pressed", String(selected));
      });
      decisionRationale.textContent = decision.rationale;
      decisionTradeoff.textContent = decision.tradeoff;
    });
  });

  const qualityOutput = document.querySelector("[data-quality-output]");
  document.querySelectorAll("[data-quality-detail]").forEach((button) => {
    button.addEventListener("click", () => {
      document.querySelectorAll("[data-quality-detail]").forEach((gate) => {
        const selected = gate === button;
        gate.classList.toggle("is-selected", selected);
        gate.setAttribute("aria-pressed", String(selected));
      });
      qualityOutput.textContent = button.dataset.qualityDetail;
      qualityOutput.classList.remove("is-updated");
      void qualityOutput.offsetWidth;
      qualityOutput.classList.add("is-updated");
    });
  });

  const requestedSlide = slides.findIndex((slide) => slide.id === location.hash.slice(1));
  goToSlide(requestedSlide >= 0 ? requestedSlide : 0, requestedSlide < 0);
})();
