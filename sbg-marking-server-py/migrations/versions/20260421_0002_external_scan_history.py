"""external_scan_history — audit of external (non-pool) mark scans

Revision ID: 0002_external_scan_history
Revises: 0001_initial
Create Date: 2026-04-21
"""
from alembic import op
import sqlalchemy as sa

revision = "0002_external_scan_history"
down_revision = "0001_initial"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "external_scan_history",
        sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=True),
        sa.Column("scanned_mark", sa.String(length=256), nullable=False),
        sa.Column("product_type", sa.String(length=64), nullable=True),
        sa.Column("gtin", sa.String(length=64), nullable=True),
        sa.Column("item", sa.String(length=128), nullable=True),
        sa.Column("operation_id", sa.String(length=128), nullable=True),
        sa.Column("receipt_id", sa.String(length=128), nullable=True),
        sa.Column("shop_id", sa.String(length=64), nullable=True),
        sa.Column("pos_id", sa.String(length=64), nullable=True),
        sa.Column("cashier_id", sa.String(length=64), nullable=True),
        sa.Column("suggested_mark", sa.String(length=256), nullable=True),
        sa.Column(
            "available_count",
            sa.BigInteger(),
            nullable=False,
            server_default=sa.text("0"),
        ),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("idx_ext_scan_mark", "external_scan_history", ["scanned_mark"])
    op.create_index("idx_ext_scan_created_at", "external_scan_history", ["created_at"])


def downgrade() -> None:
    op.drop_index("idx_ext_scan_created_at", table_name="external_scan_history")
    op.drop_index("idx_ext_scan_mark", table_name="external_scan_history")
    op.drop_table("external_scan_history")
