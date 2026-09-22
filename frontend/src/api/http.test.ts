// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from "vitest";
import {
  AUTH_SESSION_INVALIDATED_EVENT,
  clearSession,
  readSession,
  writeSession,
} from "../auth/session";
import type { AuthSession } from "../types/auth";
import { ApiError, http } from "./http";

const session: AuthSession = {
  accessToken: "signed-token",
  tokenType: "Bearer",
  expiresAt: "2099-01-01T00:00:00Z",
  user: { username: "admin", displayName: "Admin", role: "ADMIN" },
};

describe("authenticated HTTP client", () => {
  afterEach(() => {
    clearSession();
    vi.unstubAllGlobals();
  });

  it("adds the bearer token to JSON and PDF requests", async () => {
    writeSession(session);
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ ok: true }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      )
      .mockResolvedValueOnce(
        new Response("pdf", {
          status: 200,
          headers: {
            "Content-Type": "application/pdf",
            "Content-Disposition": 'attachment; filename="quote.pdf"',
          },
        }),
      );
    vi.stubGlobal("fetch", fetchMock);

    await http.get("/api/test");
    await http.download("/api/test.pdf", "fallback.pdf");

    expect(
      (fetchMock.mock.calls[0][1].headers as Headers).get("Authorization"),
    ).toBe("Bearer signed-token");
    expect(
      (fetchMock.mock.calls[1][1].headers as Headers).get("Authorization"),
    ).toBe("Bearer signed-token");
  });

  it("clears and notifies the provider on 401", async () => {
    writeSession(session);
    const invalidated = vi.fn();
    window.addEventListener(AUTH_SESSION_INVALIDATED_EVENT, invalidated, {
      once: true,
    });
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            detail: "A valid Bearer access token is required",
          }),
          {
            status: 401,
            headers: { "Content-Type": "application/problem+json" },
          },
        ),
      ),
    );

    await expect(http.get("/api/test")).rejects.toMatchObject({ status: 401 });

    expect(readSession()).toBeNull();
    expect(invalidated).toHaveBeenCalledOnce();
  });

  it("keeps the session and gives a clear message on 403", async () => {
    writeSession(session);
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            detail: "You do not have permission to perform this operation",
          }),
          {
            status: 403,
            headers: { "Content-Type": "application/problem+json" },
          },
        ),
      ),
    );

    const request = http.post("/api/materials", {});

    await expect(request).rejects.toBeInstanceOf(ApiError);
    await expect(request).rejects.toMatchObject({
      message: "No tienes permisos para realizar esta operación.",
    });
    expect(readSession()).toEqual(session);
  });

  it.each([
    [
      "Printer 7 is already occupied",
      "La impresora ya está ocupada por otra pieza en proceso.",
    ],
    [
      "Printer 7 is MAINTENANCE and cannot start production",
      "La impresora ya no está disponible para nuevas asignaciones.",
    ],
    [
      "An IN_PROGRESS item must have an assigned printer",
      "Una pieza en proceso debe tener una impresora asignada.",
    ],
    [
      "An item can only be IN_PROGRESS when its order is IN_PRODUCTION",
      "La orden debe estar en producción antes de iniciar una pieza.",
    ],
    [
      "An IN_PROGRESS item must be BLOCKED before reassigning its printer",
      "Bloquea la pieza antes de cambiar la impresora asignada.",
    ],
    [
      "Production order item status cannot change from PENDING to COMPLETED",
      "Ese cambio de estado de la pieza no está permitido.",
    ],
    [
      "BUSY is managed by production and cannot be set manually",
      "El estado Ocupada se gestiona automáticamente desde producción.",
    ],
    [
      "A BUSY printer cannot be archived",
      "La impresora no puede cambiar de estado ni archivarse mientras está ocupada.",
    ],
    [
      "Production order item changed after it was loaded; reload and retry",
      "La pieza cambió en otra sesión. Actualiza la orden antes de intentarlo de nuevo.",
    ],
    [
      "The resource is being modified by another request; retry the operation",
      "Otra operación está modificando este recurso. Actualiza los datos e inténtalo de nuevo.",
    ],
  ])("localizes production rule errors: %s", async (detail, expected) => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ detail }), {
          status: 400,
          headers: { "Content-Type": "application/problem+json" },
        }),
      ),
    );

    await expect(
      http.patch("/api/production-orders/1/items/2", {}),
    ).rejects.toMatchObject({ message: expected });
  });
});
