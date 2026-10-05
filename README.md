# LoginPage

A modular login page to build things on top of. React (Vite) in the browser, Supabase Auth for
identity. It ships sign-in, forgotten password, reset password and accept-an-invitation flows,
plus a protected home page that you replace with your app.

## Quick start

```sh
./run.sh            # checks Node, installs, then runs setup on first use and starts the dev server
```

or by hand:

```sh
npm install
npm run setup       # name, colour theme, then Supabase
npm run dev         # http://localhost:5180
```

`npm run dev` runs setup itself the first time (`predev`), so a fresh checkout never starts with
the configuration screen.

## What setup does

**Brand.** It asks for an app name and a colour theme (eight presets or any hex colour). From
that one colour it generates every themed token in `src/styles/theme.css`: hover, tint, focus
ring, the dark side panel and its text, link colour and button text colour. It checks each one
against WCAG contrast, so a light colour such as yellow gets dark button text and darker links
automatically. It also writes `public/favicon.svg` and the name into `src/brand.json`.

The sign-in page copy (headline, blurb, highlight pills and footer) is in `src/brand.json`. Edit
it by hand; setup leaves those fields alone.

**Supabase.** Paste a personal access token
([dashboard → account → tokens](https://supabase.com/dashboard/account/tokens)), and setup:

1. lists your projects and lets you pick one,
2. fetches the project URL, the **publishable** key and the **secret** key,
3. adds this app's redirect URLs (`http://localhost:5180/**`, `http://localhost:4180/**` and your
   production URL, if you give one) to **Authentication → URL Configuration**, keeping the ones
   already there. It only replaces the site URL if it is still the `localhost:3000` default,
4. offers to turn off public sign-ups. The page has no sign-up form, but the API accepts sign-ups
   until you do,
5. shows every auth change and asks before applying it,
6. stores the URL and keys in the macOS login keychain and writes `.env` and `.env.server`,
7. optionally creates a first account so you can sign in straight away.

The token is used for that run only and is never written anywhere. Set `SUPABASE_ACCESS_TOKEN`
to skip the prompt. Leave the prompt blank to paste the URL and keys yourself instead.

| File          | Holds                                              | Committed |
|---------------|----------------------------------------------------|-----------|
| `.env`        | `VITE_SUPABASE_URL`, `VITE_SUPABASE_PUBLISHABLE_KEY` | no        |
| `.env.server` | `SUPABASE_URL`, `SUPABASE_SECRET_KEY` (mode 600)   | no        |
| keychain      | `<slug>:SUPABASE_URL`, `…PUBLISHABLE_KEY`, `…SECRET_KEY` | —   |

Only `VITE_` values reach the browser. The publishable key is public by design. The secret key
bypasses row level security and can create or delete any account, so it lives only in
`.env.server` and the keychain. The app refuses to start if a secret key ends up in
`VITE_SUPABASE_PUBLISHABLE_KEY`.

### Commands

```sh
npm run setup                    # everything
npm run setup -- brand           # just the name and colour theme
npm run setup -- supabase        # just the Supabase URL, keys and auth settings
npm run setup -- sync            # fresh clone: rebuild .env files from the keychain
npm run setup -- status          # what is configured (never prints secrets)
npm run setup -- forget all      # remove the keychain entries (or name the keys)
```

The keychain entries are named after the `slug` in `src/brand.json`. It is set once on the first
run and kept when you rename the app, so `sync` keeps finding them. Outside macOS, or with
`SETUP_NO_KEYCHAIN=1`, setup just writes the `.env` files.

## Layout

```
src/
  brand.json                 name, accent and sign-in copy (written by setup)
  lib/supabase.js            client, config validation, redirect error parsing
  lib/recovery.js            remembers a password-reset link across reloads
  lib/invitation.js          has an invited user finished setting up?
  lib/palette.js             one accent colour → every themed token, contrast-checked
  context/AuthContext.jsx    session state and auth actions (useAuth)
  components/AuthLayout.jsx  split screen: branded panel + form
  components/ProtectedRoute  sends users to /login, /invite or /reset-password as needed
  components/ConfigError     shown instead of the app when Supabase is not configured
  pages/                     Login, Invite, ResetPassword, Home
  styles/                    tokens.css (neutral), theme.css (generated), app.css
scripts/setup.mjs            the setup script (no dependencies)
public/env.js                runtime config hook for hosted builds
```

## Building on top

Add routes in `src/App.jsx` and wrap anything private in `<ProtectedRoute>`. `useAuth()` gives
you `user`, `session`, `signOut` and the rest. Replace `src/pages/Home.jsx` with your app.

For a hosted build, you can inject config at container start instead of baking it in. Write
`window.__APP_ENV__ = { VITE_SUPABASE_URL: "…", VITE_SUPABASE_PUBLISHABLE_KEY: "…" }` to
`/env.js`. It takes precedence over the values in `.env`.

### Accounts

Accounts are by invitation. Invite people from the Supabase dashboard (**Authentication → Users →
Invite**). The link brings them to `/invite` to choose a name and password. Password reset emails
link back to `/reset-password`. Both need this app's URL in the redirect allow list, which setup
adds for you.

## Tests

```sh
npm test
```
