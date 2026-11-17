const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_vault_123";
const BOB_UID = "bob_vault_456";

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

test("Unauthenticated user: denied across all VAULTORA collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("purchases").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("medicines").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("notificationStates").get());
});

test("UserProfile: owner create, partial update, immutable & ghost-field rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      fullName: "Aarav Sharma",
      email: "aarav@example.com",
      themePreference: "DARK",
      purchaseRemindersEnabled: true,
      medicineRemindersEnabled: true,
      returnReminderDaysBefore: 3,
      warrantyReminderDaysBefore: 14,
      medicineReminderDaysBefore: 14,
      appLockEnabled: false,
      seededSampleData: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      fullName: "Aarav K. Sharma",
      themePreference: "LIGHT",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cross-user isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      fullName: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Shadow/ghost field & immutable createdAt rejection
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("PurchaseRecord: create, list, partial update, delete & negative validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("purchases").doc("pur_1").set({
      userId: ALICE_UID,
      productName: "MacBook Air M3",
      merchantName: "Croma",
      purchasePriceInr: 114900,
      purchaseDateMillis: Date.now() - 864000000,
      invoiceNumber: "CRM-2026-991",
      category: "Electronics",
      attachmentType: "NONE",
      returnDeadlineMillis: Date.now() + 5 * 86400000,
      warrantyDurationMonths: 12,
      warrantyExpiryMillis: Date.now() + 300 * 86400000,
      returnReminderSet: true,
      warrantyReminderSet: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("purchases").get()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("purchases").doc("pur_1").update({
      merchantName: "Croma Retail",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject @DocumentId "id" key injection
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("purchases").doc("pur_2").set({
      id: "pur_2",
      userId: ALICE_UID,
      productName: "Sony Headphones",
      merchantName: "Amazon India",
      purchasePriceInr: 24990,
      purchaseDateMillis: Date.now() - 100000,
      invoiceNumber: "AMZ-123",
      category: "Electronics",
      attachmentType: "NONE",
      returnReminderSet: false,
      warrantyReminderSet: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("purchases").doc("pur_future").set({
      userId: ALICE_UID,
      productName: "Future Item",
      merchantName: "Store",
      purchasePriceInr: 500,
      purchaseDateMillis: Date.now() - 100000,
      invoiceNumber: "INV-1",
      category: "General",
      attachmentType: "NONE",
      returnReminderSet: false,
      warrantyReminderSet: false,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: past,
    })
  );

  // Cross-user denial
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("purchases").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("purchases").doc("pur_1").delete());

  // Owner delete succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("purchases").doc("pur_1").delete()
  );
});

test("MedicineRecord & NotificationState: owner CRUD and cross-user denial", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("medicines").doc("med_1").set({
      userId: ALICE_UID,
      medicineName: "Dolo 650",
      batchNumber: "DL-8841B",
      expiryDateMillis: Date.now() + 10 * 86400000,
      quantity: "2 Strips",
      storageNote: "Cool dry cabinet",
      reminderEnabled: true,
      reminderDaysBefore: 14,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("medicines").get()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("medicines").doc("med_1").update({
      quantity: "1 Strip",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notificationStates").doc("medicine_expiry_med_1").set({
      userId: ALICE_UID,
      alertKey: "medicine_expiry_med_1",
      readFlag: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("medicines").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("notificationStates").get());
});

test("Smart Priority Engine: optional priority fields, clearing optional timestamps, and validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 5000);

  // UserProfile with SPE notification prefs
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      fullName: "Aarav SPE",
      email: "aarav@example.com",
      themePreference: "DARK",
      purchaseRemindersEnabled: true,
      medicineRemindersEnabled: true,
      returnReminderDaysBefore: 3,
      warrantyReminderDaysBefore: 14,
      medicineReminderDaysBefore: 14,
      appLockEnabled: false,
      seededSampleData: true,
      priorityCriticalAlertsEnabled: true,
      priorityHighAlertsEnabled: true,
      priorityMediumDigestEnabled: false,
      priorityLowMuted: true,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Purchase with SPE fields
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("purchases").doc("pur_spe").set({
      userId: ALICE_UID,
      productName: "OLED Monitor",
      merchantName: "Amazon India",
      purchasePriceInr: 45999,
      purchaseDateMillis: Date.now() - 100000,
      invoiceNumber: "AMZ-SPE-1",
      category: "Electronics",
      attachmentType: "NONE",
      returnReminderSet: true,
      warrantyReminderSet: true,
      importanceLevel: "CRITICAL",
      completed: false,
      warrantyClaimActive: true,
      warrantyClaimFollowUpMillis: Date.now() + 2 * 86400000,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Partial update marking completed
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("purchases").doc("pur_spe").update({
      completed: true,
      completedAtMillis: Date.now() - 100,
      updatedAt: new Date(Date.now() - 50),
    })
  );

  // Reopen and clear completedAtMillis & warrantyClaimFollowUpMillis (mirrors edit/reopen save)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("purchases").doc("pur_spe").set({
      userId: ALICE_UID,
      productName: "OLED Monitor",
      merchantName: "Amazon India",
      purchasePriceInr: 45999,
      purchaseDateMillis: Date.now() - 100000,
      invoiceNumber: "AMZ-SPE-1",
      category: "Electronics",
      attachmentType: "NONE",
      returnReminderSet: true,
      warrantyReminderSet: true,
      importanceLevel: "CRITICAL",
      completed: false,
      warrantyClaimActive: false,
      createdAt: past,
      updatedAt: new Date(Date.now() - 10),
    })
  );

  // Legacy medicine doc without SPE fields, followed by partial SPE update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("medicines").doc("med_legacy").set({
      userId: ALICE_UID,
      medicineName: "Paracetamol",
      batchNumber: "PCM-11",
      expiryDateMillis: Date.now() + 5 * 86400000,
      reminderEnabled: true,
      reminderDaysBefore: 14,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("medicines").doc("med_legacy").update({
      importanceLevel: "HIGH",
      completed: true,
      completedAtMillis: Date.now() - 100,
      updatedAt: new Date(Date.now() - 10),
    })
  );

  // Reject invalid importanceLevel
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("medicines").doc("med_legacy").update({
      importanceLevel: "URGENT_INVALID",
      updatedAt: new Date(Date.now() - 5),
    })
  );

  // Reject negative completedAtMillis
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("purchases").doc("pur_spe").update({
      completedAtMillis: -50,
      updatedAt: new Date(Date.now() - 5),
    })
  );
});

test("Warranty Claim Ready Mode: brand and manual claim status tracking & validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("purchases").doc("pur_claim").set({
      userId: ALICE_UID,
      productName: "Noise Cancelling Headphones",
      brand: "Sony",
      merchantName: "Croma",
      purchasePriceInr: 22990,
      purchaseDateMillis: Date.now() - 864000000,
      invoiceNumber: "CRM-8812",
      category: "Electronics",
      attachmentType: "IMAGE",
      attachmentUri: "content://vault/receipt.jpg",
      warrantyDurationMonths: 12,
      warrantyExpiryMillis: Date.now() + 200 * 86400000,
      returnReminderSet: false,
      warrantyReminderSet: true,
      claimStatus: "NOT_SUBMITTED",
      claimNotes: "",
      claimIssueSummary: "",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Manual status transition & notes save
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("purchases").doc("pur_claim").update({
      claimStatus: "SUBMITTED",
      claimNotes: "Ticket #SNY-9921 opened on support portal.",
      claimIssueSummary: "Left earcup crackling at high volume",
      claimUpdatedAtMillis: Date.now() - 100,
      warrantyClaimActive: true,
      updatedAt: new Date(Date.now() - 50),
    })
  );

  // Valid terminal claim status
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("purchases").doc("pur_claim").update({
      claimStatus: "RESOLVED",
      warrantyClaimActive: false,
      updatedAt: new Date(Date.now() - 10),
    })
  );

  // Reject invalid claimStatus
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("purchases").doc("pur_claim").update({
      claimStatus: "AUTO_SUBMITTED_FAKE",
      updatedAt: new Date(Date.now() - 5),
    })
  );
});

test("Family Vault Sharing: create, invite lookup, join, explicit share, and strict private isolation", async () => {
  const CHARLIE_UID = "charlie_outsider_789";
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const past = new Date(Date.now() - 5000);
  const FAMILY_ID = "fam_sharma_1";
  const INVITE_CODE = "VLT-8K2M-9P4Q";

  // Seed Alice & Bob user profiles
  const baseProfile = (uid, name, email) => ({
    userId: uid,
    fullName: name,
    email,
    themePreference: "DARK",
    purchaseRemindersEnabled: true,
    medicineRemindersEnabled: true,
    returnReminderDaysBefore: 3,
    warrantyReminderDaysBefore: 14,
    medicineReminderDaysBefore: 14,
    appLockEnabled: false,
    seededSampleData: true,
    createdAt: past,
    updatedAt: past,
  });
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(baseProfile(ALICE_UID, "Alice", "alice@example.com")));
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).set(baseProfile(BOB_UID, "Bob", "bob@example.com")));

  // Alice has a private purchase and a private medicine
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("purchases").doc("pur_priv").set({
      userId: ALICE_UID,
      productName: "Private Laptop",
      brand: "Apple",
      merchantName: "Imagine",
      purchasePriceInr: 120000,
      purchaseDateMillis: Date.now() - 100000,
      invoiceNumber: "IMG-1",
      category: "Electronics",
      attachmentType: "NONE",
      returnReminderSet: true,
      warrantyReminderSet: true,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("medicines").doc("med_priv").set({
      userId: ALICE_UID,
      medicineName: "Vitamin D3",
      batchNumber: "VD-01",
      expiryDateMillis: Date.now() + 30 * 86400000,
      reminderEnabled: true,
      reminderDaysBefore: 14,
      createdAt: past,
      updatedAt: past,
    })
  );

  // 1. Alice creates family, ADMIN member record, invite code, and links profile
  await assertSucceeds(
    aliceDb.collection("families").doc(FAMILY_ID).set({
      name: "Sharma Family",
      adminUid: ALICE_UID,
      inviteCode: INVITE_CODE,
      memberUids: [ALICE_UID],
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertSucceeds(
    aliceDb.collection("families").doc(FAMILY_ID).collection("members").doc(ALICE_UID).set({
      familyId: FAMILY_ID,
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      role: "ADMIN",
      inviteCodeUsed: INVITE_CODE,
      joinedAtMillis: Date.now() - 4000,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertSucceeds(
    aliceDb.collection("familyInvites").doc(INVITE_CODE).set({
      inviteCode: INVITE_CODE,
      familyId: FAMILY_ID,
      familyName: "Sharma Family",
      createdByUid: ALICE_UID,
      active: true,
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      familyId: FAMILY_ID,
      familyRole: "ADMIN",
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Listing all invite codes is denied (prevents scraping)
  await assertFails(bobDb.collection("familyInvites").get());
  // Exact invite code lookup works
  await assertSucceeds(bobDb.collection("familyInvites").doc(INVITE_CODE).get());

  // Outsider Charlie cannot join without valid update (e.g., trying to become admin or modify name)
  await assertFails(
    charlieDb.collection("families").doc(FAMILY_ID).update({
      name: "Hacked Family",
      memberUids: [ALICE_UID, CHARLIE_UID],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // 2. Bob joins via active invite code (only adds self to memberUids)
  await assertSucceeds(
    bobDb.collection("families").doc(FAMILY_ID).update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: new Date(Date.now() - 1500),
    })
  );

  // Bob cannot join with spoofed ADMIN role
  await assertFails(
    bobDb.collection("families").doc(FAMILY_ID).collection("members").doc(BOB_UID).set({
      familyId: FAMILY_ID,
      userId: BOB_UID,
      displayName: "Bob",
      email: "bob@example.com",
      role: "ADMIN",
      inviteCodeUsed: INVITE_CODE,
      joinedAtMillis: Date.now() - 1000,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Bob creates legitimate MEMBER record
  await assertSucceeds(
    bobDb.collection("families").doc(FAMILY_ID).collection("members").doc(BOB_UID).set({
      familyId: FAMILY_ID,
      userId: BOB_UID,
      displayName: "Bob",
      email: "bob@example.com",
      role: "MEMBER",
      inviteCodeUsed: INVITE_CODE,
      joinedAtMillis: Date.now() - 1000,
      createdAt: past,
      updatedAt: past,
    })
  );

  // 3. Privacy Isolation Check: Being in the same family NEVER grants access to private records
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("purchases").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("medicines").get());

  // Outsider Charlie cannot read family, members, or shared records
  await assertFails(charlieDb.collection("families").doc(FAMILY_ID).get());
  await assertFails(charlieDb.collection("families").doc(FAMILY_ID).collection("members").get());
  await assertFails(charlieDb.collection("families").doc(FAMILY_ID).collection("sharedRecords").get());

  // 4. Alice explicitly shares pur_priv -> Bob can read sharedRecords
  const SHARE_ID = `${ALICE_UID}_purchase_pur_priv`;
  await assertSucceeds(
    aliceDb.collection("families").doc(FAMILY_ID).collection("sharedRecords").doc(SHARE_ID).set({
      familyId: FAMILY_ID,
      ownerUid: ALICE_UID,
      ownerName: "Alice",
      recordType: "PURCHASE",
      sourceRecordId: "pur_priv",
      title: "Private Laptop",
      brand: "Apple",
      merchantOrBatch: "Imagine",
      category: "Electronics",
      priceInr: 120000,
      purchaseDateMillis: Date.now() - 100000,
      invoiceNumber: "IMG-1",
      importanceLevel: "HIGH",
      claimStatus: "NOT_SUBMITTED",
      sharedAtMillis: Date.now() - 500,
      createdAt: past,
      updatedAt: past,
    })
  );

  await assertSucceeds(bobDb.collection("families").doc(FAMILY_ID).collection("sharedRecords").get());

  // Bob cannot delete Alice's shared record (Alice is owner & admin)
  await assertFails(
    bobDb.collection("families").doc(FAMILY_ID).collection("sharedRecords").doc(SHARE_ID).delete()
  );

  // Bob cannot spoof ownerUid when sharing
  await assertFails(
    bobDb.collection("families").doc(FAMILY_ID).collection("sharedRecords").doc("spoof_share").set({
      familyId: FAMILY_ID,
      ownerUid: ALICE_UID,
      ownerName: "Alice",
      recordType: "MEDICINE",
      sourceRecordId: "med_x",
      title: "Spoof Med",
      brand: "",
      merchantOrBatch: "B-1",
      category: "Medicine Vault",
      invoiceNumber: "",
      importanceLevel: "",
      claimStatus: "NOT_SUBMITTED",
      sharedAtMillis: Date.now() - 100,
      createdAt: past,
      updatedAt: past,
    })
  );

  // Non-admin Bob cannot remove Alice's member doc
  await assertFails(
    bobDb.collection("families").doc(FAMILY_ID).collection("members").doc(ALICE_UID).delete()
  );

  // Admin Alice can remove Bob's member doc and remove Bob from memberUids
  await assertSucceeds(
    aliceDb.collection("families").doc(FAMILY_ID).collection("members").doc(BOB_UID).delete()
  );
  await assertSucceeds(
    aliceDb.collection("families").doc(FAMILY_ID).update({
      memberUids: [ALICE_UID],
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Once removed, Bob immediately loses access to sharedRecords
  await assertFails(bobDb.collection("families").doc(FAMILY_ID).collection("sharedRecords").get());
});


