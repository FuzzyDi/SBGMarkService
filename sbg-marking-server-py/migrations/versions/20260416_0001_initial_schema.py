"""initial schema — marks, reservations, idempotency_entries

Revision ID: 0001_initial
Revises:
Create Date: 2026-04-16
"""
from alembic import op
import sqlalchemy as sa

revision = "0001_initial"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "marks",
        sa.Column("mark_code", sa.String(length=256), primary_key=True),
        sa.Column("item", sa.String(length=128), nullable=True),
        sa.Column("gtin", sa.String(length=64), nullable=True),
        sa.Column("product_type", sa.String(length=64), nullable=False),
        sa.Column("valid", sa.Boolean(), nullable=False, server_default=sa.true()),
        sa.Column("blocked", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("status", sa.String(length=32), nullable=False),
        sa.Column("fifo_ts", sa.DateTime(timezone=True), nullable=False),
        sa.Column("active_reservation_id", sa.String(length=64), nullable=True),
        sa.Column("last_sale_receipt_id", sa.String(length=128), nullable=True),
        sa.Column("last_return_receipt_id", sa.String(length=128), nullable=True),
    )
    op.create_index(
        "idx_marks_selection",
        "marks",
        ["product_type", "item", "gtin", "status", "valid", "blocked", "fifo_ts"],
    )

    op.create_table(
        "reservations",
        sa.Column("id", sa.String(length=64), primary_key=True),
        sa.Column("operation_id", sa.String(length=128), nullable=False),
        sa.Column("mark_code", sa.String(length=256), nullable=False),
        sa.Column("type", sa.String(length=32), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(
            ["mark_code"], ["marks.mark_code"], name="fk_reservation_mark"
        ),
    )
    op.create_index("idx_reservations_mark_code", "reservations", ["mark_code"])
    op.create_index("idx_reservations_expires_at", "reservations", ["expires_at"])

    op.create_table(
        "idempotency_entries",
        sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=True),
        sa.Column("route", sa.String(length=64), nullable=False),
        sa.Column("operation_id", sa.String(length=128), nullable=False),
        sa.Column("response_type", sa.String(length=128), nullable=False),
        sa.Column("response_payload", sa.Text(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.UniqueConstraint("route", "operation_id", name="uq_idempotency_route_operation"),
    )
    op.create_index(
        "idx_idempotency_route_op",
        "idempotency_entries",
        ["route", "operation_id"],
    )


def downgrade() -> None:
    op.drop_index("idx_idempotency_route_op", table_name="idempotency_entries")
    op.drop_table("idempotency_entries")
    op.drop_index("idx_reservations_expires_at", table_name="reservations")
    op.drop_index("idx_reservations_mark_code", table_name="reservations")
    op.drop_table("reservations")
    op.drop_index("idx_marks_selection", table_name="marks")
    op.drop_table("marks")
