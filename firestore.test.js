const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_12345";
const BOB_UID = "bob_uid_67890";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      balanceCents: 0,
      pendingDepositCents: 0,
      botActive: true,
      botRiskProfile: "EQUILIBRE",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("transactions").get());
});

test("UserWallet: owner can create and update valid wallet; rejects ghost fields & future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Dupont",
      email: "alice@example.com",
      balanceCents: 0,
      pendingDepositCents: 0,
      botActive: true,
      botRiskProfile: "EQUILIBRE",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Update bot config & masked IBAN
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      botActive: false,
      botRiskProfile: "DYNAMIQUE",
      savedIbanMasked: "FR76 •••• •••• 0189",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject negative balance
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      balanceCents: -100,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      email: "bob@example.com",
      balanceCents: 0,
      pendingDepositCents: 0,
      botActive: false,
      botRiskProfile: "PRUDENT",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Cross-user isolation: Bob cannot read or modify Alice wallet or transactions", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const past = new Date(Date.now() - 10000);
    await db.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      balanceCents: 5000,
      pendingDepositCents: 0,
      botActive: true,
      botRiskProfile: "EQUILIBRE",
      createdAt: past,
      updatedAt: past,
    });
    await db.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_1").set({
      userId: ALICE_UID,
      type: "DEPOSIT",
      amountCents: 5000,
      currency: "EUR",
      ibanMasked: "FR76 •••• 0189",
      accountHolderName: "Alice Dupont",
      stripeId: "pi_test_123",
      status: "SUCCEEDED",
      balanceCredited: true,
      createdAt: past,
      updatedAt: past,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ balanceCents: 0 }));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("transactions").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_1").get());
});

test("SepaTransaction: enforces >= 1000 cents (10 EUR), terminal state locking, and rejects id field", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 5000);

  // Reject < 10 EUR (e.g., 999 cents)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_under_min").set({
      userId: ALICE_UID,
      type: "DEPOSIT",
      amountCents: 999,
      currency: "EUR",
      ibanMasked: "FR76 •••• 0189",
      accountHolderName: "Alice Dupont",
      stripeId: "pi_under_min",
      status: "PROCESSING",
      balanceCredited: false,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Reject @DocumentId 'id' inside map
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_with_id").set({
      id: "tx_with_id",
      userId: ALICE_UID,
      type: "DEPOSIT",
      amountCents: 1000,
      currency: "EUR",
      ibanMasked: "FR76 •••• 0189",
      accountHolderName: "Alice Dupont",
      stripeId: "pi_with_id",
      status: "PROCESSING",
      balanceCredited: false,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Valid 10.00 EUR deposit
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_valid_1").set({
      userId: ALICE_UID,
      type: "DEPOSIT",
      amountCents: 1000,
      currency: "EUR",
      ibanMasked: "DEPOSIT_IBAN",
      accountHolderName: "Alice Dupont",
      stripeId: "pi_valid_1",
      status: "PROCESSING",
      balanceCredited: false,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Transition PROCESSING -> SUCCEEDED
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_valid_1").update({
      status: "SUCCEEDED",
      balanceCredited: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Terminal state locking: cannot modify after SUCCEEDED
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_valid_1").update({
      status: "CANCELED",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Subcollection list succeeds for owner
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").get()
  );
});

test("AiAnalysisRecord & BotChatMessage: owner create & list succeed; update denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 3000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("ai_analyses").doc("an_1").set({
      userId: ALICE_UID,
      riskProfile: "EQUILIBRE",
      recommendedAction: "RÉÉQUILIBRER",
      riskScore: 5,
      projectedYieldPercent: 6.4,
      summary: "Allocation diversifiée optimale.",
      allocationsText: "60% ETF Monde, 30% Obligations Euro, 10% Trésorerie",
      createdAt: past,
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("ai_analyses").doc("an_1").update({
      riskScore: 9,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("bot_messages").doc("msg_1").set({
      userId: ALICE_UID,
      sender: "USER",
      text: "Quel est mon rendement estimé ?",
      createdAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("bot_messages").get()
  );
});
