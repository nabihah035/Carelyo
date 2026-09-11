/**
 * Skipped minification because the original files appears to be already minified.
 * Original file: /npm/@supabase/supabase-js@2.112.3/dist/umd/supabase.js
 *
 * Do NOT use SRI with dynamically generated files! More information: https://www.jsdelivr.com/using-sri-with-dynamic-files
 */
var supabase = (function (e) {
  // --- Utility functions ---
  function t(e, t) {
    var n = {};
    for (var r in e)
      Object.prototype.hasOwnProperty.call(e, r) &&
        t.indexOf(r) < 0 &&
        (n[r] = e[r]);
    if (e != null && typeof Object.getOwnPropertySymbols == `function`)
      for (var i = 0, r = Object.getOwnPropertySymbols(e); i < r.length; i++)
        t.indexOf(r[i]) < 0 &&
          Object.prototype.propertyIsEnumerable.call(e, r[i]) &&
          (n[r[i]] = e[r[i]]);
    return n;
  }

  function n(e, t, n, r) {
    function i(e) {
      return e instanceof n ? e : new n(function (t) {
        t(e);
      });
    }
    return new (n ||= Promise)(function (n, a) {
      function o(e) {
        try {
          c(r.next(e));
        } catch (e) {
          a(e);
        }
      }
      function s(e) {
        try {
          c(r.throw(e));
        } catch (e) {
          a(e);
        }
      }
      function c(e) {
        e.done ? n(e.value) : i(e.value).then(o, s);
      }
      c((r = r.apply(e, t || [])).next());
    });
  }

  let r = (e) => (e ? (...t) => e(...t) : (...e) => fetch(...e));

  // --- FunctionsError classes ---
  var i = class extends Error {
    constructor(e, t = `FunctionsError`, n) {
      super(e);
      this.name = t;
      this.context = n;
    }
    toJSON() {
      return {
        name: this.name,
        message: this.message,
        context: this.context,
      };
    }
  };

  var a = class extends i {
    constructor(e) {
      super(`Failed to send a request to the Edge Function`, `FunctionsFetchError`, e);
    }
  };

  var o = class extends i {
    constructor(e) {
      super(`Relay Error invoking the Edge Function`, `FunctionsRelayError`, e);
    }
  };

  var s = class extends i {
    constructor(e) {
      super(`Edge Function returned a non-2xx status code`, `FunctionsHttpError`, e);
    }
  };

  // --- Function regions ---
  var c;
  (function (e) {
    e.Any = `any`;
    e.ApNortheast1 = `ap-northeast-1`;
    e.ApNortheast2 = `ap-northeast-2`;
    e.ApSouth1 = `ap-south-1`;
    e.ApSoutheast1 = `ap-southeast-1`;
    e.ApSoutheast2 = `ap-southeast-2`;
    e.CaCentral1 = `ca-central-1`;
    e.EuCentral1 = `eu-central-1`;
    e.EuWest1 = `eu-west-1`;
    e.EuWest2 = `eu-west-2`;
    e.EuWest3 = `eu-west-3`;
    e.SaEast1 = `sa-east-1`;
    e.UsEast1 = `us-east-1`;
    e.UsWest1 = `us-west-1`;
    e.UsWest2 = `us-west-2`;
  })(c ||= {});

  // --- FunctionsClient ---
  var l = class {
    constructor(e, { headers: t = {}, customFetch: n, region: i = c.Any } = {}) {
      this.url = e;
      this.headers = t;
      this.region = i;
      this.fetch = r(n);
    }

    setAuth(e) {
      this.headers.Authorization = `Bearer ${e}`;
    }

    invoke(e) {
      return n(this, arguments, void 0, function* (e, t = {}) {
        var n;
        let r, i, c;
        try {
          let {
            headers: n,
            method: l,
            body: u,
            signal: d,
            timeout: f,
          } = t;
          let p = {},
            { region: m } = t;
          m ||= this.region;
          let h = new URL(`${this.url}/${e}`);
          m &&
            m !== `any` &&
            ((p[`x-region`] = m), h.searchParams.set(`forceFunctionRegion`, m));

          let g,
            ee =
              !!n &&
              Object.keys(n).some((e) => e.toLowerCase() === `content-type`);

          if (u && !ee) {
            if (
              (typeof Blob < `u` && u instanceof Blob) ||
              u instanceof ArrayBuffer
            ) {
              p[`Content-Type`] = `application/octet-stream`;
              g = u;
            } else if (typeof u == `string`) {
              p[`Content-Type`] = `text/plain`;
              g = u;
            } else if (typeof FormData < `u` && u instanceof FormData) {
              g = u;
            } else {
              p[`Content-Type`] = `application/json`;
              g = JSON.stringify(u);
            }
          } else {
            g =
              u &&
              typeof u != `string` &&
              !(typeof Blob < `u` && u instanceof Blob) &&
              !(u instanceof ArrayBuffer) &&
              !(typeof FormData < `u` && u instanceof FormData)
                ? JSON.stringify(u)
                : u;
          }

          let te = d;
          if (f) {
            i = new AbortController();
            r = setTimeout(() => i.abort(), f);
            if (d) {
              te = i.signal;
              c = () => i.abort();
              d.addEventListener(`abort`, c);
            } else {
              te = i.signal;
            }
          }

          let _ = yield this.fetch(h.toString(), {
            method: l || `POST`,
            headers: Object.assign(
              Object.assign(Object.assign({}, p), this.headers),
              n
            ),
            body: g,
            signal: te,
          }).catch((e) => {
            throw new a(e);
          });

          let ne = _.headers.get(`x-relay-error`);
          if (ne && ne === `true`) throw new o(_);
          if (!_.ok) throw new s(_);

          let re = (_.headers.get(`Content-Type`) ?? `text/plain`)
              .split(`;`)[0]
              .trim()
              .toLowerCase(),
            ie;

          if (re === `application/json`) ie = yield _.json();
          else if (re === `application/octet-stream` || re === `application/pdf`)
            ie = yield _.blob();
          else if (re === `text/event-stream`) ie = _;
          else if (re === `multipart/form-data`) ie = yield _.formData();
          else ie = yield _.text();

          return { data: ie, error: null, response: _ };
        } catch (e) {
          return {
            data: null,
            error: e,
            response: e instanceof s || e instanceof o ? e.context : void 0,
          };
        } finally {
          r && clearTimeout(r);
          c &&
            ((n = t.signal) == null || n.removeEventListener(`abort`, c));
        }
      });
    }
  };

  // --- PostgREST error ---
  let u = (e) => Math.min(1000 * 2 ** e, 30000),
    d = [520, 503],
    f = [`GET`, `HEAD`, `OPTIONS`];

  var p = class extends Error {
    constructor(e) {
      super(e.message);
      this.name = `PostgrestError`;
      this.details = e.details;
      this.hint = e.hint;
      this.code = e.code;
    }
    toJSON() {
      return {
        name: this.name,
        message: this.message,
        details: this.details,
        hint: this.hint,
        code: this.code,
      };
    }
  };

  // --- Helper functions for PostgREST ---
  function m(e) {
    `@babel/helpers - typeof`;
    return (
      (m =
        typeof Symbol == `function` && typeof Symbol.iterator == `symbol`
          ? function (e) {
              return typeof e;
            }
          : function (e) {
              return e &&
                typeof Symbol == `function` &&
                e.constructor === Symbol &&
                e !== Symbol.prototype
                ? `symbol`
                : typeof e;
            }),
      m(e)
    );
  }

  function h(e, t) {
    if (m(e) != `object` || !e) return e;
    var n = e[Symbol.toPrimitive];
    if (n !== void 0) {
      var r = n.call(e, t || `default`);
      if (m(r) != `object`) return r;
      throw TypeError(`@@toPrimitive must return a primitive value.`);
    }
    return (t === `string` ? String : Number)(e);
  }

  function g(e) {
    var t = h(e, `string`);
    return m(t) == `symbol` ? t : t + ``;
  }

  function ee(e, t, n) {
    return (
      (t = g(t)) in e
        ? Object.defineProperty(e, t, {
            value: n,
            enumerable: !0,
            configurable: !0,
            writable: !0,
          })
        : (e[t] = n),
      e
    );
  }

  function te(e, t) {
    var n = Object.keys(e);
    if (Object.getOwnPropertySymbols) {
      var r = Object.getOwnPropertySymbols(e);
      t &&
        (r = r.filter(function (t) {
          return Object.getOwnPropertyDescriptor(e, t).enumerable;
        })),
        n.push.apply(n, r);
    }
    return n;
  }

  function _(e) {
    for (var t = 1; t < arguments.length; t++) {
      var n = arguments[t] == null ? {} : arguments[t];
      t % 2
        ? te(Object(n), !0).forEach(function (t) {
            ee(e, t, n[t]);
          })
        : Object.getOwnPropertyDescriptors
          ? Object.defineProperties(e, Object.getOwnPropertyDescriptors(n))
          : te(Object(n)).forEach(function (t) {
              Object.defineProperty(
                e,
                t,
                Object.getOwnPropertyDescriptor(n, t)
              );
            });
    }
    return e;
  }

  function ne(e, t) {
    return new Promise((n) => {
      if (t?.aborted) {
        n();
        return;
      }
      let r = setTimeout(() => {
        t?.removeEventListener(`abort`, i);
        n();
      }, e);
      function i() {
        clearTimeout(r);
        n();
      }
      t?.addEventListener(`abort`, i);
    });
  }

  function re(e, t, n, r) {
    return !(!r || n >= 3 || !f.includes(e) || !d.includes(t));
  }

  // --- PostgrestBuilder ---
  var ie = class {
    constructor(e) {
      this.shouldThrowOnError = !1;
      this.retryEnabled = !0;
      this.method = e.method;
      this.url = e.url;
      this.headers = new Headers(e.headers);
      this.schema = e.schema;
      this.body = e.body;
      this.shouldThrowOnError = e.shouldThrowOnError ?? !1;
      this.signal = e.signal;
      this.isMaybeSingle = e.isMaybeSingle ?? !1;
      this.shouldStripNulls = e.shouldStripNulls ?? !1;
      this.urlLengthLimit = e.urlLengthLimit ?? 8000;
      this.retryEnabled = e.retry ?? !0;
      if (e.fetch) this.fetch = e.fetch;
      else this.fetch = fetch;
    }

    throwOnError() {
      return (this.shouldThrowOnError = !0), this;
    }

    stripNulls() {
      if (this.headers.get(`Accept`) === `text/csv`)
        throw Error(`stripNulls() cannot be used with csv()`);
      return (this.shouldStripNulls = !0), this;
    }

    setHeader(e, t) {
      return (
        (this.headers = new Headers(this.headers)),
        this.headers.set(e, t),
        this
      );
    }

    retry(e) {
      return (this.retryEnabled = e), this;
    }

    then(e, t) {
      var n = this;
      if (
        (this.schema === void 0 ||
          ([`GET`, `HEAD`].includes(this.method)
            ? this.headers.set(`Accept-Profile`, this.schema)
            : this.headers.set(`Content-Profile`, this.schema)),
        this.method !== `GET` &&
          this.method !== `HEAD` &&
          this.headers.set(`Content-Type`, `application/json`),
        this.shouldStripNulls)
      ) {
        let e = this.headers.get(`Accept`);
        if (e === `application/vnd.pgrst.object+json`)
          this.headers.set(
            `Accept`,
            `application/vnd.pgrst.object+json;nulls=stripped`
          );
        else if (!e || e === `application/json`)
          this.headers.set(
            `Accept`,
            `application/vnd.pgrst.array+json;nulls=stripped`
          );
      }

      let r = this.fetch,
        i = (async () => {
          let e = 0;
          for (;;) {
            let t = {};
            n.headers.forEach((e, n) => {
              t[n] = e;
            });
            if (e > 0) t[`X-Retry-Count`] = String(e);

            let i;
            try {
              i = await r(n.url.toString(), {
                method: n.method,
                headers: t,
                body: JSON.stringify(n.body, (e, t) =>
                  typeof t == `bigint` ? t.toString() : t
                ),
                signal: n.signal,
              });
            } catch (t) {
              if (
                t?.name === `AbortError` ||
                t?.code === `ABORT_ERR` ||
                !f.includes(n.method)
              )
                throw t;
              if (n.retryEnabled && e < 3) {
                let t = u(e);
                e++;
                await ne(t, n.signal);
                continue;
              }
              throw t;
            }

            if (re(n.method, i.status, e, n.retryEnabled)) {
              let t = i.headers?.get(`Retry-After`) ?? null,
                r =
                  t === null
                    ? u(e)
                    : Math.max(0, parseInt(t, 10) || 0) * 1000;
              await i.text();
              e++;
              await ne(r, n.signal);
              continue;
            }

            return await n.processResponse(i);
          }
        })();

      if (!this.shouldThrowOnError) {
        i = i.catch((e) => {
          let t = ``,
            n = ``,
            r = ``,
            i = e?.cause;
          if (i) {
            let n = i?.message ?? ``,
              r = i?.code ?? ``;
            t = `${e?.name ?? `FetchError`}: ${e?.message}`;
            t += `\n\nCaused by: ${i?.name ?? `Error`}: ${n}`;
            if (r) t += ` (${r})`;
            if (i?.stack) t += `\n${i.stack}`;
          } else {
            t = e?.stack ?? ``;
          }

          let a = this.url.toString().length;
          if (e?.name === `AbortError` || e?.code === `ABORT_ERR`) {
            r = ``;
            n = `Request was aborted (timeout or manual cancellation)`;
            if (a > this.urlLengthLimit)
              n += `. Note: Your request URL is ${a} characters, which may exceed server limits. If selecting many fields, consider using views. If filtering with large arrays (e.g., .in('id', [many IDs])), consider using an RPC function to pass values server-side.`;
          } else if (
            i?.name === `HeadersOverflowError` ||
            i?.code === `UND_ERR_HEADERS_OVERFLOW`
          ) {
            r = ``;
            n = `HTTP headers exceeded server limits (typically 16KB)`;
            if (a > this.urlLengthLimit)
              n += `. Your request URL is ${a} characters. If selecting many fields, consider using views. If filtering with large arrays (e.g., .in('id', [200+ IDs])), consider using an RPC function instead.`;
          }

          return {
            success: !1,
            error: {
              message: `${e?.name ?? `FetchError`}: ${e?.message}`,
              details: t,
              hint: n,
              code: r,
            },
            data: null,
            count: null,
            status: 0,
            statusText: ``,
          };
        });
      }

      return i.then(e, t);
    }

    async processResponse(e) {
      var t = this;
      let n = null,
        r = null,
        i = null,
        a = e.status,
        o = e.statusText;

      if (e.ok) {
        if (t.method !== `HEAD`) {
          let i = await e.text();
          if (i !== ``) {
            if (t.headers.get(`Accept`) === `text/csv`) {
              r = i;
            } else if (
              t.headers.get(`Accept`) &&
              t.headers.get(`Accept`)?.includes(`application/vnd.pgrst.plan+text`)
            ) {
              r = i;
            } else {
              try {
                r = JSON.parse(i);
              } catch {
                n = { message: i };
                r = null;
                if (t.shouldThrowOnError)
                  throw new p({ message: i, details: ``, hint: ``, code: `` });
              }
            }
          }
        }

        let s = t.headers.get(`Prefer`)?.match(/count=(exact|planned|estimated)/),
          c = e.headers.get(`content-range`)?.split(`/`);

        if (s && c && c.length > 1) i = parseInt(c[1]);

        if (t.isMaybeSingle && Array.isArray(r)) {
          if (r.length > 1) {
            n = {
              code: `PGRST116`,
              details: `Results contain ${r.length} rows, application/vnd.pgrst.object+json requires 1 row`,
              hint: null,
              message: `JSON object requested, multiple (or no) rows returned`,
            };
            r = null;
            i = null;
            a = 406;
            o = `Not Acceptable`;
            if (t.shouldThrowOnError)
              throw new p(_(_({}, n), {}, { hint: n.hint ?? `` }));
          } else {
            r = r.length === 1 ? r[0] : null;
          }
        }
      } else {
        let i = await e.text();
        try {
          n = JSON.parse(i);
          if (Array.isArray(n) && e.status === 404) {
            r = [];
            n = null;
            a = 200;
            o = `OK`;
          }
        } catch {
          if (e.status === 404 && i === ``) {
            a = 204;
            o = `No Content`;
          } else {
            n = { message: i };
          }
        }
        if (n && t.shouldThrowOnError) throw new p(n);
      }

      return {
        success: n === null,
        error: n,
        data: r,
        count: i,
        status: a,
        statusText: o,
      };
    }

    returns() {
      return this;
    }

    overrideTypes() {
      return this;
    }
  };

  // --- PostgrestTransformBuilder ---
  var ae = class extends ie {
    throwOnError() {
      return super.throwOnError();
    }

    select(e) {
      let t = !1,
        n = (e ?? `*`)
          .split(``)
          .map((e) =>
            /\s/.test(e) && !t ? `` : (e === `"` && (t = !t), e)
          )
          .join(``);
      return (
        this.url.searchParams.set(`select`, n),
        this.headers.append(`Prefer`, `return=representation`),
        this
      );
    }

    order(
      e,
      {
        ascending: t = !0,
        nullsFirst: n,
        foreignTable: r,
        referencedTable: i = r,
      } = {}
    ) {
      let a = i ? `${i}.order` : `order`,
        o = this.url.searchParams.get(a);
      return (
        this.url.searchParams.set(
          a,
          `${o ? `${o},` : ``}${e}.${t ? `asc` : `desc`}${
            n === void 0 ? `` : n ? `.nullsfirst` : `.nullslast`
          }`
        ),
        this
      );
    }

    limit(e, { foreignTable: t, referencedTable: n = t } = {}) {
      let r = n === void 0 ? `limit` : `${n}.limit`;
      return this.url.searchParams.set(r, `${e}`), this;
    }

    range(e, t, { foreignTable: n, referencedTable: r = n } = {}) {
      let i = r === void 0 ? `offset` : `${r}.offset`,
        a = r === void 0 ? `limit` : `${r}.limit`;
      return (
        this.url.searchParams.set(i, `${e}`),
        this.url.searchParams.set(a, `${t - e + 1}`),
        this
      );
    }

    abortSignal(e) {
      return (this.signal = e), this;
    }

    single() {
      return (
        this.headers.set(`Accept`, `application/vnd.pgrst.object+json`),
        this
      );
    }

    maybeSingle() {
      return (this.isMaybeSingle = !0), this;
    }

    csv() {
      return this.headers.set(`Accept`, `text/csv`), this;
    }

    geojson() {
      return this.headers.set(`Accept`, `application/geo+json`), this;
    }

    explain({
      analyze: e = !1,
      verbose: t = !1,
      settings: n = !1,
      buffers: r = !1,
      wal: i = !1,
      format: a = `text`,
    } = {}) {
      let o = [
          e ? `analyze` : null,
          t ? `verbose` : null,
          n ? `settings` : null,
          r ? `buffers` : null,
          i ? `wal` : null,
        ]
          .filter(Boolean)
          .join(`|`),
        s = this.headers.get(`Accept`) ?? `application/json`;
      return (
        this.headers.set(
          `Accept`,
          `application/vnd.pgrst.plan+${a}; for="${s}"; options=${o};`
        ),
        this
      );
    }

    rollback() {
      return this.headers.append(`Prefer`, `tx=rollback`), this;
    }

    returns() {
      return this;
    }

    maxAffected(e) {
      return (
        this.headers.append(`Prefer`, `handling=strict`),
        this.headers.append(`Prefer`, `max-affected=${e}`),
        this
      );
    }
  };

  let oe = RegExp(`[,()]`);

  // --- PostgrestFilterBuilder ---
  var se = class extends ae {
    throwOnError() {
      return super.throwOnError();
    }

    eq(e, t) {
      return this.url.searchParams.append(e, `eq.${t}`), this;
    }
    neq(e, t) {
      return this.url.searchParams.append(e, `neq.${t}`), this;
    }
    gt(e, t) {
      return this.url.searchParams.append(e, `gt.${t}`), this;
    }
    gte(e, t) {
      return this.url.searchParams.append(e, `gte.${t}`), this;
    }
    lt(e, t) {
      return this.url.searchParams.append(e, `lt.${t}`), this;
    }
    lte(e, t) {
      return this.url.searchParams.append(e, `lte.${t}`), this;
    }
    like(e, t) {
      return this.url.searchParams.append(e, `like.${t}`), this;
    }
    likeAllOf(e, t) {
      return (
        this.url.searchParams.append(e, `like(all).{${t.join(`,`)}}`), this
      );
    }
    likeAnyOf(e, t) {
      return (
        this.url.searchParams.append(e, `like(any).{${t.join(`,`)}}`), this
      );
    }
    ilike(e, t) {
      return this.url.searchParams.append(e, `ilike.${t}`), this;
    }
    ilikeAllOf(e, t) {
      return (
        this.url.searchParams.append(e, `ilike(all).{${t.join(`,`)}}`), this
      );
    }
    ilikeAnyOf(e, t) {
      return (
        this.url.searchParams.append(e, `ilike(any).{${t.join(`,`)}}`), this
      );
    }
    regexMatch(e, t) {
      return this.url.searchParams.append(e, `match.${t}`), this;
    }
    regexIMatch(e, t) {
      return this.url.searchParams.append(e, `imatch.${t}`), this;
    }
    is(e, t) {
      return this.url.searchParams.append(e, `is.${t}`), this;
    }
    isDistinct(e, t) {
      return this.url.searchParams.append(e, `isdistinct.${t}`), this;
    }
    in(e, t) {
      let n = Array.from(new Set(t))
        .map((e) => (typeof e == `string` && oe.test(e) ? `"${e}"` : `${e}`))
        .join(`,`);
      return this.url.searchParams.append(e, `in.(${n})`), this;
    }
    notIn(e, t) {
      let n = Array.from(new Set(t))
        .map((e) => (typeof e == `string` && oe.test(e) ? `"${e}"` : `${e}`))
        .join(`,`);
      return this.url.searchParams.append(e, `not.in.(${n})`), this;
    }
    contains(e, t) {
      return (
        typeof t == `string`
          ? this.url.searchParams.append(e, `cs.${t}`)
          : Array.isArray(t)
            ? this.url.searchParams.append(e, `cs.{${t.join(`,`)}}`)
            : this.url.searchParams.append(e, `cs.${JSON.stringify(t)}`),
        this
      );
    }
    containedBy(e, t) {
      return (
        typeof t == `string`
          ? this.url.searchParams.append(e, `cd.${t}`)
          : Array.isArray(t)
            ? this.url.searchParams.append(e, `cd.{${t.join(`,`)}}`)
            : this.url.searchParams.append(e, `cd.${JSON.stringify(t)}`),
        this
      );
    }
    rangeGt(e, t) {
      return this.url.searchParams.append(e, `sr.${t}`), this;
    }
    rangeGte(e, t) {
      return this.url.searchParams.append(e, `nxl.${t}`), this;
    }
    rangeLt(e, t) {
      return this.url.searchParams.append(e, `sl.${t}`), this;
    }
    rangeLte(e, t) {
      return this.url.searchParams.append(e, `nxr.${t}`), this;
    }
    rangeAdjacent(e, t) {
      return this.url.searchParams.append(e, `adj.${t}`), this;
    }
    overlaps(e, t) {
      return (
        typeof t == `string`
          ? this.url.searchParams.append(e, `ov.${t}`)
          : this.url.searchParams.append(e, `ov.{${t.join(`,`)}}`),
        this
      );
    }
    textSearch(e, t, { config: n, type: r } = {}) {
      let i = ``;
      if (r === `plain`) i = `pl`;
      else if (r === `phrase`) i = `ph`;
      else if (r === `websearch`) i = `w`;
      let a = n === void 0 ? `` : `(${n})`;
      return this.url.searchParams.append(e, `${i}fts${a}.${t}`), this;
    }
    match(e) {
      return (
        Object.entries(e)
          .filter(([e, t]) => t !== void 0)
          .forEach(([e, t]) => {
            this.url.searchParams.append(e, `eq.${t}`);
          }),
        this
      );
    }
    not(e, t, n) {
      return this.url.searchParams.append(e, `not.${t}.${n}`), this;
    }
    or(e, { foreignTable: t, referencedTable: n = t } = {}) {
      let r = n ? `${n}.or` : `or`;
      return this.url.searchParams.append(r, `(${e})`), this;
    }
    filter(e, t, n) {
      return this.url.searchParams.append(e, `${t}.${n}`), this;
    }
  };

  // --- PostgrestQueryBuilder ---
  var ce = class {
    constructor(
      e,
      {
        headers: t = {},
        schema: n,
        fetch: r,
        urlLengthLimit: i = 8000,
        retry: a,
      }
    ) {
      this.url = e;
      this.headers = new Headers(t);
      this.schema = n;
      this.fetch = r;
      this.urlLengthLimit = i;
      this.retry = a;
    }

    cloneRequestState() {
      return {
        url: new URL(this.url.toString()),
        headers: new Headers(this.headers),
      };
    }

    select(e, t) {
      let { head: n = !1, count: r } = t ?? {},
        i = n ? `HEAD` : `GET`,
        a = !1,
        o = (e ?? `*`)
          .split(``)
          .map((e) =>
            /\s/.test(e) && !a ? `` : (e === `"` && (a = !a), e)
          )
          .join(``),
        { url: s, headers: c } = this.cloneRequestState();

      return (
        s.searchParams.set(`select`, o),
        r && c.append(`Prefer`, `count=${r}`),
        new se({
          method: i,
          url: s,
          headers: c,
          schema: this.schema,
          fetch: this.fetch,
          urlLengthLimit: this.urlLengthLimit,
          retry: this.retry,
        })
      );
    }

    insert(e, { count: t, defaultToNull: n = !0 } = {}) {
      let { url: r, headers: i } = this.cloneRequestState();
      if (
        (t && i.append(`Prefer`, `count=${t}`),
        n || i.append(`Prefer`, `missing=default`),
        Array.isArray(e))
      ) {
        let t = e.reduce((e, t) => e.concat(Object.keys(t)), []);
        if (t.length > 0) {
          let e = [...new Set(t)].map((e) => `"${e}"`);
          r.searchParams.set(`columns`, e.join(`,`));
        }
      }
      return new se({
        method: `POST`,
        url: r,
        headers: i,
        schema: this.schema,
        body: e,
        fetch: this.fetch ?? fetch,
        urlLengthLimit: this.urlLengthLimit,
        retry: this.retry,
      });
    }

    upsert(
      e,
      {
        onConflict: t,
        ignoreDuplicates: n = !1,
        count: r,
        defaultToNull: i = !0,
      } = {}
    ) {
      let { url: a, headers: o } = this.cloneRequestState();
      if (
        (o.append(
          `Prefer`,
          `resolution=${n ? `ignore` : `merge`}-duplicates`
        ),
        t !== void 0 && a.searchParams.set(`on_conflict`, t),
        r && o.append(`Prefer`, `count=${r}`),
        i || o.append(`Prefer`, `missing=default`),
        Array.isArray(e))
      ) {
        let t = e.reduce((e, t) => e.concat(Object.keys(t)), []);
        if (t.length > 0) {
          let e = [...new Set(t)].map((e) => `"${e}"`);
          a.searchParams.set(`columns`, e.join(`,`));
        }
      }
      return new se({
        method: `POST`,
        url: a,
        headers: o,
        schema: this.schema,
        body: e,
        fetch: this.fetch ?? fetch,
        urlLengthLimit: this.urlLengthLimit,
        retry: this.retry,
      });
    }

    update(e, { count: t } = {}) {
      let { url: n, headers: r } = this.cloneRequestState();
      return (
        t && r.append(`Prefer`, `count=${t}`),
        new se({
          method: `PATCH`,
          url: n,
          headers: r,
          schema: this.schema,
          body: e,
          fetch: this.fetch ?? fetch,
          urlLengthLimit: this.urlLengthLimit,
          retry: this.retry,
        })
      );
    }

    delete({ count: e } = {}) {
      let { url: t, headers: n } = this.cloneRequestState();
      return (
        e && n.append(`Prefer`, `count=${e}`),
        new se({
          method: `DELETE`,
          url: t,
          headers: n,
          schema: this.schema,
          fetch: this.fetch ?? fetch,
          urlLengthLimit: this.urlLengthLimit,
          retry: this.retry,
        })
      );
    }
  };

  // --- PostgrestClient ---
  var le = class e {
    constructor(
      e,
      {
        headers: t = {},
        schema: n,
        fetch: r,
        timeout: i,
        urlLengthLimit: a = 8000,
        retry: o,
      } = {}
    ) {
      this.url = e;
      this.headers = new Headers(t);
      this.schemaName = n;
      this.urlLengthLimit = a;

      let s = r ?? globalThis.fetch;

      if (i !== void 0 && i > 0) {
        this.fetch = (e, t) => {
          let n = new AbortController(),
            r = setTimeout(() => n.abort(), i),
            a = t?.signal;

          if (a) {
            if (a.aborted) return clearTimeout(r), s(e, t);
            let i = () => {
              clearTimeout(r);
              n.abort();
            };
            return (
              a.addEventListener(`abort`, i, { once: !0 }),
              s(e, _(_({}, t), {}, { signal: n.signal })).finally(() => {
                clearTimeout(r);
                a.removeEventListener(`abort`, i);
              })
            );
          }

          return s(e, _(_({}, t), {}, { signal: n.signal })).finally(() =>
            clearTimeout(r)
          );
        };
      } else {
        this.fetch = s;
      }

      this.retry = o;
    }

    from(e) {
      if (!e || typeof e != `string` || e.trim() === ``)
        throw Error(`Invalid relation name: relation must be a non-empty string.`);
      return new ce(new URL(`${this.url}/${e}`), {
        headers: new Headers(this.headers),
        schema: this.schemaName,
        fetch: this.fetch,
        urlLengthLimit: this.urlLengthLimit,
        retry: this.retry,
      });
    }

    schema(t) {
      return new e(this.url, {
        headers: this.headers,
        schema: t,
        fetch: this.fetch,
        urlLengthLimit: this.urlLengthLimit,
        retry: this.retry,
      });
    }

    rpc(e, t = {}, { head: n = !1, get: r = !1, count: i } = {}) {
      let a,
        o = new URL(`${this.url}/rpc/${e}`),
        s,
        c = (e) =>
          typeof e == `object` && !!e && (!Array.isArray(e) || e.some(c)),
        l = n && Object.values(t).some(c);

      if (l) {
        a = `POST`;
        s = t;
      } else if (n || r) {
        a = n ? `HEAD` : `GET`;
        Object.entries(t)
          .filter(([e, t]) => t !== void 0)
          .map(([e, t]) => [
            e,
            Array.isArray(t) ? `{${t.join(`,`)}}` : `${t}`,
          ])
          .forEach(([e, t]) => {
            o.searchParams.append(e, t);
          });
      } else {
        a = `POST`;
        s = t;
      }

      let u = new Headers(this.headers);
      return (
        l
          ? u.set(
              `Prefer`,
              i ? `count=${i},return=minimal` : `return=minimal`
            )
          : i && u.set(`Prefer`, `count=${i}`),
        new se({
          method: a,
          url: o,
          headers: u,
          schema: this.schemaName,
          body: s,
          fetch: this.fetch ?? fetch,
          urlLengthLimit: this.urlLengthLimit,
          retry: this.retry,
        })
      );
    }
  };

  // --- WebSocket detection and Phoenix protocol (Realtime) ---
  // ... (the rest of the Realtime implementation, Storage, Auth, etc.)

  // --- SupabaseClient ---
  var Ki = class {
    constructor(e, t, n) {
      this.supabaseUrl = e;
      this.supabaseKey = t;

      let r = Bn(e);
      if (!t) throw Error(`supabaseKey is required.`);

      Mn(t);

      this.realtimeUrl = new URL(`realtime/v1`, r);
      this.realtimeUrl.protocol = this.realtimeUrl.protocol.replace(`http`, `ws`);
      this.authUrl = new URL(`auth/v1`, r);
      this.storageUrl = new URL(`storage/v1`, r);
      this.functionsUrl = new URL(`functions/v1`, r);

      let i = `sb-${r.hostname.split(`.`)[0]}-auth-token`,
        a = {
          db: vn,
          realtime: bn,
          auth: { ...yn, storageKey: i },
          global: _n,
          tracePropagation: xn,
        },
        o = zn(n ?? {}, a);

      this.settings = o;
      this.storageKey = o.auth.storageKey ?? ``;
      this.headers = o.global.headers ?? {};

      if (o.accessToken) {
        this.accessToken = o.accessToken;
        this.auth = new Proxy(
          {},
          {
            get: (e, t) => {
              throw Error(
                `@supabase/supabase-js: Supabase Client is configured with the accessToken option, accessing supabase.auth.${String(t)} is not possible`
              );
            },
          }
        );
      } else {
        this.auth = this._initSupabaseAuthClient(
          o.auth ?? {},
          this.headers,
          o.global.fetch
        );
      }

      this.fetch = Nn(
        t,
        e,
        this._getSessionToken.bind(this),
        o.global.fetch,
        o.tracePropagation
      );
      this.functionsFetch = Nn(
        t,
        e,
        this._getSessionToken.bind(this),
        o.global.fetch,
        o.tracePropagation,
        { omitApiKeyAsBearer: !0 }
      );
      this.realtime = this._initRealtimeClient({
        headers: this.headers,
        accessToken: this._getAccessToken.bind(this),
        fetch: this.fetch,
        ...o.realtime,
      });

      if (this.accessToken)
        Promise.resolve(this.accessToken())
          .then((e) => this.realtime.setAuth(e))
          .catch((e) =>
            console.warn(`Failed to set initial Realtime auth token:`, e)
          );

      this.rest = new le(new URL(`rest/v1`, r).href, {
        headers: this.headers,
        schema: o.db.schema,
        fetch: this.fetch,
        timeout: o.db.timeout,
        urlLengthLimit: o.db.urlLengthLimit,
        retry: o.db.retry,
      });

      this.storage = new pn(
        this.storageUrl.href,
        this.headers,
        this.fetch,
        n?.storage
      );

      if (!o.accessToken) this._listenForAuthEvents();
    }

    get functions() {
      return new l(this.functionsUrl.href, {
        headers: this.headers,
        customFetch: this.functionsFetch,
      });
    }

    from(e) {
      return this.rest.from(e);
    }

    schema(e) {
      return this.rest.schema(e);
    }

    rpc(e, t = {}, n = { head: !1, get: !1, count: void 0 }) {
      return this.rest.rpc(e, t, n);
    }

    channel(e, t = { config: {} }) {
      return this.realtime.channel(e, t);
    }

    getChannels() {
      return this.realtime.getChannels();
    }

    removeChannel(e) {
      return this.realtime.removeChannel(e);
    }

    removeAllChannels() {
      return this.realtime.removeAllChannels();
    }

    async _getSessionToken() {
      if (this.accessToken) return await this.accessToken();
      let { data: e } = await this.auth.getSession();
      return e.session?.access_token ?? null;
    }

    async _getAccessToken() {
      return (await this._getSessionToken()) ?? this.supabaseKey;
    }

    _initSupabaseAuthClient(
      {
        autoRefreshToken: e,
        persistSession: t,
        detectSessionInUrl: n,
        storage: r,
        userStorage: i,
        storageKey: a,
        flowType: o,
        lock: s,
        debug: c,
        throwOnError: l,
        experimental: u,
        lockAcquireTimeout: d,
        skipAutoInitialize: f,
      },
      p,
      m
    ) {
      let h = {
        Authorization: `Bearer ${this.supabaseKey}`,
        apikey: `${this.supabaseKey}`,
      };
      return new Gi({
        url: this.authUrl.href,
        headers: { ...h, ...p },
        storageKey: a,
        autoRefreshToken: e,
        persistSession: t,
        detectSessionInUrl: n,
        storage: r,
        userStorage: i,
        flowType: o,
        lock: s,
        debug: c,
        throwOnError: l,
        experimental: u,
        fetch: m,
        lockAcquireTimeout: d,
        skipAutoInitialize: f,
        hasCustomAuthorizationHeader: Object.keys(this.headers).some(
          (e) => e.toLowerCase() === `authorization`
        ),
      });
    }

    _initRealtimeClient(e) {
      return new mt(this.realtimeUrl.href, {
        ...e,
        params: { apikey: this.supabaseKey, ...e?.params },
      });
    }

    _listenForAuthEvents() {
      return this.auth.onAuthStateChange((e, t) => {
        this._handleTokenChanged(e, `CLIENT`, t?.access_token);
      });
    }

    _handleTokenChanged(e, t, n) {
      (e === `TOKEN_REFRESHED` ||
        e === `SIGNED_IN` ||
        e === `INITIAL_SESSION`) &&
      this.changedAccessToken !== n
        ? ((this.changedAccessToken = n), this.realtime.setAuth(n))
        : e === `SIGNED_OUT` &&
          (this.realtime.setAuth(),
          t == `STORAGE` && this.auth.signOut(),
          (this.changedAccessToken = void 0));
    }
  };

  // --- createClient function ---
  let qi = (e, t, n) => new Ki(e, t, n);

  // --- Node.js version check ---
  function Ji() {
    if (typeof window < `u` || globalThis.Deno !== void 0) return !1;
    let e = globalThis.process;
    if (!e) return !1;
    let t = e.version;
    if (t == null) return !1;
    let n = t.match(/^v(\d+)\./);
    return n ? parseInt(n[1], 10) <= 20 : !1;
  }

  return (
    Ji() &&
      console.warn(
        `⚠️  Node.js 20 and below are deprecated and will no longer be supported in future versions of @supabase/supabase-js. Please upgrade to Node.js 22 or later. For more information, visit: https://github.com/orgs/supabase/discussions/45715`
      ),
    (e.AuthAdminApi = Ui),
    (e.AuthApiError = Jn),
    (e.AuthClient = Wi),
    (e.AuthError = qn),
    (e.AuthImplicitGrantRedirectError = Qn),
    (e.AuthInvalidCredentialsError = Zn),
    (e.AuthInvalidJwtError = lr),
    (e.AuthInvalidTokenResponseError = I),
    (e.AuthPKCECodeVerifierMissingError = tr),
    (e.AuthPKCEGrantCodeExchangeError = er),
    (e.AuthRefreshDiscardedError = ar),
    (e.AuthRetryableFetchError = rr),
    (e.AuthSessionMissingError = F),
    (e.AuthUnknownError = N),
    (e.AuthWeakPasswordError = sr),
    (e.CustomAuthError = P),
    Object.defineProperty(e, `FunctionRegion`, {
      enumerable: !0,
      get: function () {
        return c;
      },
    }),
    (e.FunctionsError = i),
    (e.FunctionsFetchError = a),
    (e.FunctionsHttpError = s),
    (e.FunctionsRelayError = o),
    (e.GoTrueAdminApi = li),
    (e.GoTrueClient = Hi),
    (e.NavigatorLockAcquireTimeoutError = fi),
    (e.PostgrestError = p),
    (e.REALTIME_CHANNEL_STATES = st),
    Object.defineProperty(e, `REALTIME_LISTEN_TYPES`, {
      enumerable: !0,
      get: function () {
        return T;
      },
    }),
    Object.defineProperty(e, `REALTIME_POSTGRES_CHANGES_LISTEN_EVENT`, {
      enumerable: !0,
      get: function () {
        return ot;
      },
    }),
    Object.defineProperty(e, `REALTIME_PRESENCE_LISTEN_EVENTS`, {
      enumerable: !0,
      get: function () {
        return qe;
      },
    }),
    Object.defineProperty(e, `REALTIME_SUBSCRIBE_STATES`, {
      enumerable: !0,
      get: function () {
        return E;
      },
    }),
    (e.RealtimeChannel = ct),
    (e.RealtimeClient = mt),
    (e.RealtimePostgresFilterBuilder = it),
    (e.RealtimePresence = Je),
    (e.SIGN_OUT_SCOPES = ci),
    (e.StorageApiError = jt),
    (e.SupabaseClient = Ki),
    (e.WebSocketFactory = ue),
    (e.createClient = qi),
    (e.isAuthApiError = Yn),
    (e.isAuthError = M),
    (e.isAuthImplicitGrantRedirectError = $n),
    (e.isAuthPKCECodeVerifierMissingError = nr),
    (e.isAuthRefreshDiscardedError = or),
    (e.isAuthRetryableFetchError = ir),
    (e.isAuthSessionMissingError = Xn),
    (e.isAuthWeakPasswordError = cr),
    (e.lockInternals = Z),
    (e.navigatorLock = mi),
    (e.postgresChangesFilter = at),
    (e.processLock = gi),
    e
  );
})({});