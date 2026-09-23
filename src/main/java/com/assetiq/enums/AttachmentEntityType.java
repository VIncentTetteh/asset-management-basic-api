package com.assetiq.enums;

/**
 * The entity kinds a {@code DocumentAttachment} can hang off.
 *
 * <p>One constant per document field in the product, so that every place the UI
 * offers "attach a file" resolves to a real, tenant-scoped, stored object rather
 * than a free-text URL the user has to host somewhere themselves:</p>
 *
 * <table border="1">
 *   <caption>Coverage</caption>
 *   <tr><th>Constant</th><th>Document field it backs</th></tr>
 *   <tr><td>{@link #CONTRACT}</td><td>{@code ContractDto.documentUrl}</td></tr>
 *   <tr><td>{@link #SOFTWARE_LICENSE}</td><td>{@code SoftwareLicenseDto.licenseDocumentUrl}</td></tr>
 *   <tr><td>{@link #EXPENSE}</td><td>{@code ExpenseDto.receiptUrl}</td></tr>
 *   <tr><td>{@link #DISPOSAL_RECORD}</td><td>{@code DisposalRecordDto.complianceDocumentUrl}</td></tr>
 *   <tr><td>{@link #SECURITY_POLICY}</td><td>security policy document</td></tr>
 *   <tr><td>{@link #COMPLIANCE_CONTROL}</td><td>compliance control evidence</td></tr>
 *   <tr><td>{@link #PCI_SAQ}</td><td>PCI SAQ evidence</td></tr>
 *   <tr><td>{@link #BOG_CONTROL}</td><td>Bank of Ghana control evidence</td></tr>
 *   <tr><td>{@link #VULNERABILITY_SCAN}</td><td>vulnerability scan report</td></tr>
 * </table>
 *
 * <p>The legacy {@code *Url} columns and DTO fields are deliberately untouched:
 * existing rows hold real URLs that must keep resolving. An attachment is an
 * additional way to carry the document, not a replacement for what is already
 * stored.</p>
 *
 * <p>Persisted by name ({@code @Enumerated(EnumType.STRING)}) into a
 * {@code VARCHAR(50)} column with no database-side enum type, so adding a
 * constant needs no migration. Never rename or remove one: the name in the
 * column is the only record of what an existing row points at.</p>
 */
public enum AttachmentEntityType {
    EXPENSE,
    COMPLIANCE_CONTROL,
    BOG_CONTROL,
    SECURITY_POLICY,
    PCI_SAQ,
    CONTRACT,
    DISPOSAL_RECORD,
    VULNERABILITY_SCAN,
    SOFTWARE_LICENSE
}
