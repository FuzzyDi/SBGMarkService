"""substitution_mapping — pre-validation KM substitution (BarcodeTransformerPlugin)

Revision ID: 0003_substitution_mapping
Revises: 0002_external_scan_history
Create Date: 2026-04-21
"""
from alembic import op
import sqlalchemy as sa

revision = "0003_substitution_mapping"
down_revision = "0002_external_scan_history"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "substitution_mapping",
        sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=True),
        sa.Column("scanned_mark", sa.String(length=256), nullable=False),
        sa.Column("substituted_mark", sa.String(length=256), nullable=False),
        sa.Column("receipt_id", sa.String(length=128), nullable=True),
        sa.Column("shop_id", sa.String(length=64), nullable=True),
        sa.Column("pos_id", sa.String(length=64), nullable=True),
        sa.Column("cashier_id", sa.String(length=64), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column(
            "consumed",
            sa.Boolean(),
            nullable=False,
            server_default=sa.false(),
        ),
        sa.ForeignKeyConstraint(
            ["substituted_mark"], ["marks.mark_code"], name="fk_subst_mapping_mark"
        ),
        sa.UniqueConstraint("scanned_mark", name="uq_subst_scanned_mark"),
    )
    op.create_index(
        "idx_subst_substituted_mark", "substitution_mapping", ["substituted_mark"]
    )
    op.create_index("idx_subst_expires_at", "substitution_mapping", ["expires_at"])


def downgrade() -> None:
    op.drop_index("idx_subst_expires_at", table_name="substitution_mapping")
    op.drop_index("idx_subst_substituted_mark", table_name="substitution_mapping")
    op.drop_table("substitution_mapping")
